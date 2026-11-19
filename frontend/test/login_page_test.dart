import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/ui/campo_codigo.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/authentication/infraestructure/ui/login_page.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/theme/app_theme.dart';

class _Gateway implements AutenticacionGateway {
  final List<bool> reemplazos = [];

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async => DesafioVerificacion(
    desafioId: 'abc',
    correo: correo,
    correoEnmascarado: 'a***@example.com',
    vigencia: const Duration(minutes: 5),
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
    expect(find.text('Revisa tu correo'), findsOneWidget);
    expect(find.textContaining('a***@example.com'), findsOneWidget);
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
    expect(find.text('Revisa tu correo'), findsOneWidget);
  });
}
