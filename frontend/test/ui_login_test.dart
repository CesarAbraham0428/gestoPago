import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/ui/campo_codigo.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/infraestructure/ui/login_page.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/theme/app_theme.dart';

import 'helpers/falsos.dart';

void main() {
  late GatewayFalso gateway;
  late SesionController sesion;

  setUp(() {
    gateway = GatewayFalso();
    sesion = SesionController(gateway: gateway, almacen: AlmacenFalso());
  });

  tearDown(() => sesion.dispose());

  Future<void> abrir(WidgetTester tester, {Size tamano = const Size(500, 900)}) async {
    tester.view.physicalSize = tamano;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(theme: AppTheme.light(), home: LoginPage(sesion: sesion)),
    );
  }

  Finder campo(String etiqueta) => find.widgetWithText(TextFormField, etiqueta);

  Future<void> llenarCredenciales(WidgetTester tester) async {
    await tester.enterText(campo('Correo electrónico'), 'ana@example.com');
    await tester.enterText(campo('Contraseña'), 'Segura123!');
  }

  testWidgets('en pantalla ancha el formulario va centrado, sin panel lateral', (tester) async {
    await abrir(tester, tamano: const Size(1200, 800));
    expect(find.textContaining('Recarga tu celular'), findsNothing);
    expect(find.text('GestoPago'), findsOneWidget);
    expect(find.text('Inicia sesión'), findsOneWidget);
  });

  testWidgets('credenciales vacías no se envían', (tester) async {
    await abrir(tester);
    await tester.tap(find.text('Continuar'));
    await tester.pump();
    expect(find.text('Escribe tu correo electrónico.'), findsOneWidget);
    expect(find.text('Escribe tu contraseña.'), findsOneWidget);
  });

  testWidgets('mostrar y ocultar la contraseña', (tester) async {
    await abrir(tester);
    await tester.tap(find.byTooltip('Mostrar contraseña'));
    await tester.pump();
    expect(find.byTooltip('Ocultar contraseña'), findsOneWidget);
    await tester.tap(find.byTooltip('Ocultar contraseña'));
    await tester.pump();
    expect(find.byTooltip('Mostrar contraseña'), findsOneWidget);
  });

  testWidgets('error de la API y error inesperado al pedir el código', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);

    gateway.errorIniciar = const ApiException(401, 'Credenciales incorrectas');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.text('Credenciales incorrectas'), findsOneWidget);

    gateway.errorIniciar = StateError('raro');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.textContaining('No fue posible iniciar sesión'), findsOneWidget);
  });

  testWidgets('enviar con la tecla "hecho" y mostrar un aviso previo', (tester) async {
    await sesion.verificarCodigo(desafio: desafioPrueba, codigo: '123456');
    await sesion.cerrarSesion(aviso: 'Cerramos tu sesión por inactividad.');
    await abrir(tester);
    expect(find.text('Cerramos tu sesión por inactividad.'), findsOneWidget);

    await llenarCredenciales(tester);
    await tester.tap(campo('Contraseña'));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.text('Revisa tu correo'), findsOneWidget);
  });

  testWidgets('código incorrecto, error inesperado, teclado y volver', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();

    gateway.errorVerificar = const ApiException(401, 'Código incorrecto');
    await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(find.text('Código incorrecto'), findsOneWidget);

    gateway.errorVerificar = StateError('raro');
    await tester.tap(find.byKey(CampoCodigo.claveCampo));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.textContaining('No fue posible verificar'), findsOneWidget);

    await tester.tap(find.text('Volver e iniciar de nuevo'));
    await tester.pumpAndSettle();
    expect(find.text('Inicia sesión'), findsOneWidget);
  });

  testWidgets('tras credenciales incorrectas la contraseña conserva el foco y se puede escribir', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);
    gateway.errorIniciar = const ApiException(401, 'Correo o contraseña incorrectos');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.text('Correo o contraseña incorrectos'), findsOneWidget);

    final password = tester.widget<EditableText>(
      find.descendant(of: campo('Contraseña'), matching: find.byType(EditableText)),
    );
    expect(password.focusNode.hasFocus, isTrue);
    tester.testTextInput.enterText('Otra123!');
    await tester.pump();
    expect(password.controller.text, 'Otra123!');
  });

  testWidgets('verificación exitosa abre la sesión', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(sesion.autenticado, isTrue);
    await sesion.cerrarSesion(); // cancela el temporizador de vencimiento
  });

  testWidgets('un 429 desactiva el envío con cuenta regresiva y luego lo rehabilita', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);

    gateway.errorIniciar = const ApiException(
      429,
      'Demasiados intentos; espera unos minutos',
      reintentarEn: Duration(seconds: 3),
    );
    await tester.tap(find.text('Continuar'));
    await tester.pump();
    await tester.pump();

    expect(find.text('Demasiados intentos; espera unos minutos'), findsOneWidget);
    expect(find.text('Reintenta en 3 s'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);

    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Reintenta en 2 s'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    expect(find.text('Continuar'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNotNull);
  });

  testWidgets('sin Retry-After el 429 espera un minuto por omisión', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);

    gateway.errorIniciar = const ApiException(429, 'Demasiados intentos');
    await tester.tap(find.text('Continuar'));
    await tester.pump();
    await tester.pump();

    expect(find.text('Reintenta en 1:00 min'), findsOneWidget);
    await tester.pump(const Duration(seconds: 60));
    expect(find.text('Continuar'), findsOneWidget);
  });
}
