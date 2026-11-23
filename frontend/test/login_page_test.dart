import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:frontend/core/ui/campo_codigo.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/authentication/infraestructure/ui/login_page.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/ui/widgets_estado.dart';
import 'package:frontend/core/theme/app_theme.dart';

class _Gateway implements AutenticacionGateway {
  _Gateway({this.enrolamiento});

  final EnrolamientoTotp? enrolamiento;
  final List<bool> reemplazos = [];

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async => DesafioVerificacion(
    desafioId: 'abc',
    correo: correo,
    vigencia: const Duration(minutes: 5),
    enrolamiento: enrolamiento,
  );

  /// Hay otra sesión abierta: solo entra si el usuario confirma cerrarla.
  @override
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  }) async {
    reemplazos.add(cerrarSesionAnterior);
    if (!cerrarSesionAnterior) {
      throw const SesionActivaException('Ya tienes una sesión activa');
    }
    return Sesion(
      token: 'jwt',
      correo: desafio.correo,
      nombreCompleto: 'Ana',
      expiraEn: DateTime.now().add(const Duration(hours: 1)),
    );
  }

  @override
  Future<void> cerrarSesion() async {}

  @override
  Stream<String> cierresDeSesion() => Stream<String>.multi((_) {});
}

/// Cuenta inactiva: el servidor responde 403 ya al validar la contraseña.
class _GatewayBloqueado extends _Gateway {
  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async => throw const ApiException(403, 'Tu cuenta está bloqueada.');
}

class _Almacen implements AlmacenSesion {
  @override
  Future<Sesion?> leer() async => null;

  @override
  Future<void> guardar(Sesion sesion) async {}

  @override
  Future<void> borrar() async {}
}

void main() {
  testWidgets('credenciales, código y confirmación para cerrar la otra sesión', (tester) async {
    final gateway = _Gateway();
    final sesion = SesionController(gateway: gateway, almacen: _Almacen());

    tester.view.physicalSize = const Size(500, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(theme: AppTheme.light(), home: LoginPage(sesion: sesion)),
    );

    // Paso 1: la contraseña no basta para entrar.
    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'Segura123!');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.text('Código de tu app'), findsOneWidget);
    expect(find.textContaining('app de autenticación'), findsOneWidget);
    expect(find.byType(QrImageView), findsNothing); // la app ya estaba enlazada
    expect(sesion.autenticado, isFalse);

    // Un código mal escrito no llega al servidor.
    await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(find.text('El código tiene 6 dígitos.'), findsOneWidget);
    expect(gateway.reemplazos, isEmpty);

    // Paso 2: hay otra sesión; se pide confirmar y al aceptar se entra.
    await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();
    expect(find.text('Ya tienes una sesión abierta'), findsOneWidget);
    expect(sesion.autenticado, isFalse);

    await tester.tap(find.text('Cerrar la otra sesión y continuar'));
    await tester.pumpAndSettle();
    expect(sesion.autenticado, isTrue);
    expect(gateway.reemplazos, [false, true]);

    // La sesión abierta programa su vencimiento; se cancela antes de terminar la prueba.
    sesion.dispose();
  });

  testWidgets('cancelar el diálogo no abre sesión', (tester) async {
    final gateway = _Gateway();
    final sesion = SesionController(gateway: gateway, almacen: _Almacen());
    addTearDown(sesion.dispose);

    tester.view.physicalSize = const Size(500, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(theme: AppTheme.light(), home: LoginPage(sesion: sesion)),
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'Segura123!');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123456');
    await tester.tap(find.text('Verificar y entrar'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();

    expect(sesion.autenticado, isFalse);
    expect(gateway.reemplazos, [false]);
    expect(find.text('Código de tu app'), findsOneWidget);
  });

  testWidgets('la primera vez muestra el QR y la clave en bloques para enlazar la app', (tester) async {
    final gateway = _Gateway(
      enrolamiento: const EnrolamientoTotp(
        secreto: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567',
        otpauthUri: 'otpauth://totp/GestoPago:ana?secret=ABCDEFGHIJKLMNOPQRSTUVWXYZ234567',
      ),
    );
    final sesion = SesionController(gateway: gateway, almacen: _Almacen());
    addTearDown(sesion.dispose);

    tester.view.physicalSize = const Size(500, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(theme: AppTheme.light(), home: LoginPage(sesion: sesion)),
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'Segura123!');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();

    expect(find.text('Enlaza tu app de autenticación'), findsOneWidget);
    expect(find.byType(QrImageView), findsOneWidget);
    expect(find.text('ABCD EFGH IJKL MNOP QRST UVWX YZ23 4567'), findsOneWidget);
    expect(find.byKey(CampoCodigo.claveCampo), findsOneWidget);

    String? copiado;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (llamada) async {
      if (llamada.method == 'Clipboard.setData') {
        copiado = (llamada.arguments as Map)['text'] as String?;
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    await tester.tap(find.byTooltip('Copiar clave'));
    await tester.pump();
    expect(copiado, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567');
    expect(find.text('Clave copiada'), findsOneWidget);
  });

  testWidgets('una cuenta inactiva muestra el aviso destacado de cuenta bloqueada', (tester) async {
    final sesion = SesionController(gateway: _GatewayBloqueado(), almacen: _Almacen());
    tester.view.physicalSize = const Size(500, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(theme: AppTheme.light(), home: LoginPage(sesion: sesion)),
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'Segura123!');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();

    expect(find.byType(AvisoCuentaBloqueada), findsOneWidget);
    expect(find.text('Cuenta bloqueada'), findsOneWidget);
    expect(find.text('Comunícate con soporte para reactivarla'), findsOneWidget);
    expect(find.byType(AvisoEstado), findsNothing);
  });
}
