import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/ui/campo_codigo.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/dependencias.dart';
import 'package:frontend/app/mobile/mobile_shell.dart';
import 'package:frontend/app/shell_comun.dart';
import 'package:frontend/app/web/web_shell.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  final productos = [
    producto('Recarga 50', 'Telcel', 1),
    producto('Luz', 'CFE', 3, tipo: 2, precio: 8),
  ];

  Future<Dependencias> crear({Sesion? sesion}) async {
    final pagos = PagosFalso();
    pagos.alPagar = (_) async => Comprobante(
      estado: 'APROBADO',
      monto: 50,
      comision: 0,
      registradoEn: DateTime(2026, 3, 5),
    );
    pagos.alHistorial = (n) async =>
        pagina([compra('U1', telefono: '5512345678')]);
    final deps = Dependencias.crear(
      autenticacion: GatewayFalso(),
      almacenSesion: AlmacenFalso()..guardada = sesion,
      catalogoRepositorio: CatalogoFalso(() async => productos),
      pagosRepositorio: pagos,
      cuentasRepositorio: CuentasFalso(
        () async => [
          const Cuenta(
            id: 1,
            numeroCuenta: '123456789012345678',
            saldo: 900,
            activa: true,
          ),
        ],
      ),
    );
    await deps.sesion.restaurar();
    return deps;
  }

  Future<void> abrir(
    WidgetTester tester,
    Dependencias deps,
    ConstructorShell shell, {
    Size tamano = const Size(1300, 900),
  }) async {
    tester.view.physicalSize = tamano;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(GestoPagoApp(dependencias: deps, shell: shell));
    await tester.pumpAndSettle();
  }

  Future<void> cerrar(WidgetTester tester, Dependencias deps) async {
    await tester.pumpWidget(const SizedBox());
    deps.sesion.dispose();
  }

  GoRouter router(WidgetTester tester) =>
      GoRouter.of(tester.element(find.byType(Scaffold).first));

  group('redirecciones', () {
    testWidgets('sin sesión lleva al login', (tester) async {
      final deps = await crear();
      await abrir(tester, deps, construirShellWeb);
      expect(find.text('Inicia sesión'), findsOneWidget);
      await cerrar(tester, deps);
    });

    testWidgets('del login a crear cuenta y de regreso', (tester) async {
      final deps = await crear();
      await abrir(tester, deps, construirShellWeb);
      await tester.ensureVisible(find.text('Crear cuenta'));
      await tester.tap(find.text('Crear cuenta'));
      await tester.pumpAndSettle();
      final volver = find.text('¿Ya tienes cuenta? Inicia sesión');
      expect(volver, findsOneWidget);

      await tester.ensureVisible(volver);
      await tester.tap(volver);
      await tester.pumpAndSettle();
      expect(find.text('Inicia sesión'), findsOneWidget);
      await cerrar(tester, deps);
    });

    testWidgets('con sesión, / y /login llevan al catálogo', (tester) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(tester, deps, construirShellWeb);
      expect(find.text('Elige un producto'), findsOneWidget);

      router(tester).go('/login');
      await tester.pumpAndSettle();
      expect(find.text('Elige un producto'), findsOneWidget);
      router(tester).go('/');
      await tester.pumpAndSettle();
      expect(find.text('Elige un producto'), findsOneWidget);
      await cerrar(tester, deps);
    });
  });

  group('shell web', () {
    testWidgets('barra lateral: navegar, detalle y cerrar sesión', (
      tester,
    ) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(tester, deps, construirShellWeb);
      expect(
        find.text('Ana Pérez'),
        findsNothing,
      ); // la barra ya no muestra al usuario
      expect(find.text('ana@example.com'), findsNothing);

      await tester.tap(find.text('Recarga 50'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);

      await tester.tap(find.text('Mis compras'));
      await tester.pumpAndSettle();
      expect(find.text('Elige una compra'), findsOneWidget);
      final consultas = (deps.pagos as PagosFalso).historiales;
      await tester.tap(find.text('Recarga 50'));
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
      // Elegir un pago solo muestra su recibo: no vuelve a consultar ni recarga la lista.
      expect((deps.pagos as PagosFalso).historiales, consultas);

      await tester.tap(find.text('Catálogo'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(deps.sesion.autenticado, isTrue);

      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Cerrar sesión').last);
      await tester.pumpAndSettle();
      expect(deps.sesion.autenticado, isFalse);
      expect(find.text('Inicia sesión'), findsOneWidget);
      expect(deps.catalogo.total, 0); // se descartaron los datos del usuario
      await cerrar(tester, deps);
    });

    testWidgets('pantalla angosta: barra superior y navegación inferior', (
      tester,
    ) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(
        tester,
        deps,
        construirShellWeb,
        tamano: const Size(500, 900),
      );
      expect(find.byType(NavigationBar), findsOneWidget);

      await tester.tap(find.text('Mis compras').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Recarga 50'));
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsNothing); // en el detalle
      expect(find.text('Compra'), findsWidgets);
      await tester.tap(find.byTooltip('Volver'));
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsOneWidget);

      await tester.tap(find.byTooltip('Tu cuenta'));
      await tester.pumpAndSettle();
      expect(find.text('Ana Pérez'), findsOneWidget);
      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();
      expect(find.text('¿Cerrar sesión?'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      await cerrar(tester, deps);
    });
  });

  group('compra completa desde la app', () {
    testWidgets('comprar y pasar al historial', (tester) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(tester, deps, construirShellWeb);
      await tester.tap(find.text('Recarga 50'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Número de celular'),
        '5512345678',
      );
      await tester.ensureVisible(find.text('Realizar compra'));
      await tester.tap(find.text('Realizar compra'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(CampoCodigo.claveCampo), '123456');
      await tester.tap(find.textContaining('Pagar \$'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Ver mis compras'));
      await tester.tap(find.text('Ver mis compras'));
      await tester.pumpAndSettle();
      expect(find.text('Elige una compra'), findsOneWidget);
      await cerrar(tester, deps);
    });
  });

  group('shell móvil', () {
    testWidgets('angosto: navegación inferior y menú de usuario', (
      tester,
    ) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(
        tester,
        deps,
        construirShellMovil,
        tamano: const Size(420, 900),
      );
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byType(NavigationRail), findsNothing);

      await tester.tap(find.byTooltip('Tu cuenta'));
      await tester.pumpAndSettle();
      expect(find.text('Ana Pérez'), findsOneWidget);
      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();
      expect(find.text('¿Cerrar sesión?'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Mis compras'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Catálogo').last);
      await tester.pumpAndSettle();
      await cerrar(tester, deps);
    });

    testWidgets('ancho: riel de navegación', (tester) async {
      final deps = await crear(sesion: sesionPrueba());
      await abrir(
        tester,
        deps,
        construirShellMovil,
        tamano: const Size(900, 900),
      );
      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
      await tester.tap(find.text('Mis compras'));
      await tester.pumpAndSettle();
      expect(find.text('Mis compras'), findsWidgets);
      await cerrar(tester, deps);
    });

    testWidgets('menú de usuario sin nombre completo', (tester) async {
      final sinNombre = Sesion(
        token: 't',
        correo: 'solo@example.com',
        nombreCompleto: '',
        expiraEn: DateTime.now().add(const Duration(hours: 1)),
      );
      final deps = await crear(sesion: sinNombre);
      await abrir(
        tester,
        deps,
        construirShellMovil,
        tamano: const Size(420, 900),
      );
      await tester.tap(find.byTooltip('Tu cuenta'));
      await tester.pumpAndSettle();
      expect(find.text('Tu cuenta'), findsWidgets);
      expect(find.text('solo@example.com'), findsOneWidget);
      await tester.tapAt(const Offset(5, 5)); // cerrar el menú
      await tester.pumpAndSettle();
      await cerrar(tester, deps);
    });
  });

  group('GestoPagoApp', () {
    testWidgets('cambiar las dependencias reconstruye el ámbito', (
      tester,
    ) async {
      final a = await crear(sesion: sesionPrueba());
      final b = await crear();
      await abrir(tester, a, construirShellWeb);
      await tester.pumpWidget(
        GestoPagoApp(dependencias: b, shell: construirShellWeb),
      );
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox());
      a.sesion.dispose();
      b.sesion.dispose();
    });
  });

  group('iniciarAplicacion', () {
    testWidgets('se niega a arrancar sin HTTPS en producción', (tester) async {
      await iniciarAplicacion(
        construirShellWeb,
        produccion: true,
        conexionPermitida: false,
      );
      await tester.pump();
      expect(
        find.textContaining('no puede conectarse de forma segura'),
        findsOneWidget,
      );
    });

    testWidgets('arranque normal restaura la sesión y muestra el login', (
      tester,
    ) async {
      FlutterSecureStorage.setMockInitialValues({});
      tester.view.physicalSize = const Size(500, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.runAsync(() => iniciarAplicacion(construirShellMovil));
      await tester.pumpAndSettle();
      expect(find.text('Inicia sesión'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('en producción con la URL por omisión (local) arranca', (
      tester,
    ) async {
      FlutterSecureStorage.setMockInitialValues({});
      tester.view.physicalSize = const Size(500, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.runAsync(
        () => iniciarAplicacion(construirShellMovil, produccion: true),
      );
      await tester.pumpAndSettle();
      expect(find.text('Inicia sesión'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('en producción con HTTPS permitido arranca normalmente', (
      tester,
    ) async {
      FlutterSecureStorage.setMockInitialValues({});
      tester.view.physicalSize = const Size(500, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.runAsync(
        () => iniciarAplicacion(
          construirShellMovil,
          produccion: true,
          conexionPermitida: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Inicia sesión'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });
  });
}
