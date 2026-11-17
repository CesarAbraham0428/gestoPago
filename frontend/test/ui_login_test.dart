import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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

  testWidgets('en pantalla ancha muestra el panel de marca', (tester) async {
    await abrir(tester, tamano: const Size(1200, 800));
    expect(find.textContaining('Recarga tu celular'), findsOneWidget);
    expect(find.textContaining('Elige el servicio'), findsOneWidget);
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
    await tester.enterText(campo('Código de verificación'), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(find.text('Código incorrecto'), findsOneWidget);

    gateway.errorVerificar = StateError('raro');
    await tester.tap(campo('Código de verificación'));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(find.textContaining('No fue posible verificar'), findsOneWidget);

    await tester.tap(find.text('Volver e iniciar de nuevo'));
    await tester.pumpAndSettle();
    expect(find.text('Inicia sesión'), findsOneWidget);
  });

  testWidgets('verificación exitosa abre la sesión', (tester) async {
    await abrir(tester);
    await llenarCredenciales(tester);
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    await tester.enterText(campo('Código de verificación'), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(sesion.autenticado, isTrue);
    await sesion.cerrarSesion(); // cancela el temporizador de vencimiento
  });
}
