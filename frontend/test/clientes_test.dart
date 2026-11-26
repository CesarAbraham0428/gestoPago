import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/dependencias.dart';
import 'package:frontend/app/mobile/mobile_shell.dart';
import 'package:frontend/app/shell_comun.dart';
import 'package:frontend/app/web/web_shell.dart';
import 'package:frontend/clientes/application/clientes_controller.dart';
import 'package:frontend/clientes/infraestructure/adapters/api_clientes_repository.dart';
import 'package:frontend/clientes/infraestructure/ui/clientes_page.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

Map<String, Object?> _json(int id, {String rol = 'Cliente', bool activo = true}) => {
  'id': id,
  'primerNombre': 'Nombre$id',
  'apellidoPaterno': 'Apellido',
  'apellidoMaterno': 'Ruiz',
  'fechaNacimiento': '1990-01-01',
  'curp': 'CURP$id',
  'rfc': 'RFC$id',
  'sexo': 'Femenino',
  'nacionalidad': 'Mexicana',
  'correoElectronico': 'n$id@example.com',
  'telefonoMovil': '5512345678',
  'rol': rol,
  'activo': activo,
  'fechaCreacion': '2026-01-10T12:00:00Z',
  'cuentas': <Object>[],
};

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('ApiClientesRepository', () {
    final peticiones = <String>[];

    ApiClientesRepository crear({int totalPaginas = 2}) => ApiClientesRepository(
      ApiClient(
        baseUrl: 'http://api',
        client: MockClient((r) async {
          peticiones.add('${r.method} ${r.url.path}');
          Object? datos;
          if (r.url.path == '/usuarios/me') {
            datos = {'clienteId': 1, 'correo': 'n1@example.com'};
          } else if (r.url.path == '/clientes/consultar') {
            final cuerpo = jsonDecode(r.body) as Map<String, dynamic>;
            datos = cuerpo.containsKey('correo')
                ? {'datos': [_json(1, rol: 'Administrador')]}
                : {
                    'totalPaginas': totalPaginas,
                    'datos': cuerpo['pagina'] == 0
                        ? [_json(1, rol: 'Administrador'), _json(2)]
                        : [_json(3, activo: false)],
                  };
          }
          return http.Response(
            jsonEncode({'mensaje': 'ok', 'datos': datos}),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      ),
    );

    setUp(peticiones.clear);

    test('listar recorre todas las páginas y excluye al usuario autenticado', () async {
      final clientes = await crear().listar();
      expect(clientes.map((c) => c.id), [2, 3]);
      expect(clientes.last.activo, isFalse);
      expect(clientes.first.fechaAlta, isNotNull);
    });

    test('esAdministrador lee el rol del propio cliente', () async {
      expect(await crear().esAdministrador(), isTrue);
    });

    test('cambiarEstado usa la ruta de activar o desactivar', () async {
      final repo = crear();
      await repo.cambiarEstado(rfc: 'RFC2', activo: false);
      await repo.cambiarEstado(rfc: 'RFC2', activo: true);
      expect(peticiones, ['POST /clientes/desactivar', 'POST /clientes/activar']);
    });
  });

  group('ClientesController', () {
    ClientesFalso repo() => ClientesFalso(
      admin: true,
      clientes: [
        clientePrueba(2, 'Zoe', 'Mora'),
        clientePrueba(3, 'Ana', 'Pérez', activo: false),
        clientePrueba(4, 'Luis', 'Soto'),
      ],
    );

    test('detectarRol guarda el rol y queda sin saberse si falla', () async {
      final falso = repo();
      final c = ClientesController(falso);
      expect(c.esAdministrador, isNull);
      await c.detectarRol();
      expect(c.esAdministrador, isTrue);

      falso.errorRol = Exception('sin red');
      await c.detectarRol();
      expect(c.esAdministrador, isNull);
    });

    test('carga, ordena por nombre, busca y filtra por estado', () async {
      final c = ClientesController(repo());
      await c.cargar();
      expect(c.estado, EstadoClientes.listo);
      expect(c.visibles.map((x) => x.perfil.primerNombre), ['Ana', 'Luis', 'Zoe']);
      expect((c.total, c.activos), (3, 2));

      c.buscar('SOTO');
      expect(c.visibles.map((x) => x.id), [4]);
      c.buscar('rfc3');
      expect(c.visibles.map((x) => x.id), [3]);
      c.buscar('');

      c.filtrar(FiltroEstadoCliente.inactivos);
      expect(c.visibles.map((x) => x.id), [3]);
      c.filtrar(FiltroEstadoCliente.activos);
      expect(c.visibles.map((x) => x.id), [4, 2]);
    });

    test('un error de carga se informa y cargar de nuevo lo reintenta', () async {
      final falso = repo()..errorListar = const ConexionException('sin red');
      final c = ClientesController(falso);
      await c.cargar();
      expect(c.estado, EstadoClientes.error);
      expect(c.error, 'sin red');

      falso.errorListar = null;
      await c.cargar();
      expect(c.estado, EstadoClientes.listo);
      final listados = falso.listados;
      await c.cargar(); // ya cargado: no vuelve a pedir
      expect(falso.listados, listados);
    });

    test('cambiarEstado guarda y recarga; con error devuelve el mensaje', () async {
      final falso = repo();
      final c = ClientesController(falso);
      await c.cargar();

      expect(await c.cambiarEstado(c.porId(2)!, activo: false), isNull);
      expect(falso.cambios, [(rfc: 'RFC2', activo: false)]);
      expect(c.porId(2)!.activo, isFalse);
      expect(c.cambiandoId, isNull);

      falso.errorCambiar = const ApiException(409, 'No se pudo');
      expect(await c.cambiarEstado(c.porId(4)!, activo: false), 'No se pudo');
      expect(c.porId(4)!.activo, isTrue);
    });

    test('si falla la recarga tras cambiar, se pide actualizar la lista', () async {
      final falso = repo();
      final c = ClientesController(falso);
      await c.cargar();
      falso.errorListar = Exception('sin red');

      expect(await c.cambiarEstado(c.porId(2)!, activo: false), isNull);
      expect(c.estado, EstadoClientes.error);
    });

    test('limpiar descarta todo, incluido el rol', () async {
      final c = ClientesController(repo());
      await c.detectarRol();
      await c.cargar();
      c.limpiar();
      expect((c.estado, c.total, c.esAdministrador), (EstadoClientes.inicial, 0, null));
    });
  });

  group('ClientesPage', () {
    Future<(ClientesController, ClientesFalso)> abrir(
      WidgetTester tester, {
      int? id,
      bool admin = true,
      List<int>? seleccionados,
    }) async {
      final falso = ClientesFalso(
        admin: admin,
        clientes: [
          clientePrueba(2, 'Zoe', 'Mora'),
          clientePrueba(3, 'Ana', 'Pérez', activo: false),
        ],
      );
      final c = ClientesController(falso);
      await c.detectarRol();
      await montar(
        tester,
        ClientesPage(
          controller: c,
          idSeleccionado: id,
          alSeleccionar: (i) => seleccionados?.add(i),
          alVolver: () {},
        ),
        tamano: const Size(900, 1200),
      );
      await tester.pumpAndSettle();
      return (c, falso);
    }

    testWidgets('muestra una card por cliente con nombre, correo y estado', (tester) async {
      final elegidos = <int>[];
      await abrir(tester, seleccionados: elegidos);

      expect(find.text('Ana Pérez Ruiz'), findsOneWidget);
      expect(find.text('ana@example.com'), findsOneWidget);
      expect(find.text('Zoe Mora Ruiz'), findsOneWidget);
      expect(find.text('Activa'), findsOneWidget);
      expect(find.text('Inactiva'), findsOneWidget);
      expect(find.byIcon(Icons.person), findsNWidgets(2));

      await tester.tap(find.text('Zoe Mora Ruiz'));
      expect(elegidos, [2]);
    });

    testWidgets('busca y filtra por estado', (tester) async {
      await abrir(tester);

      await tester.tap(find.text('Inactivos (1)'));
      await tester.pumpAndSettle();
      expect(find.text('Ana Pérez Ruiz'), findsOneWidget);
      expect(find.text('Zoe Mora Ruiz'), findsNothing);

      await tester.tap(find.text('Todos (2)'));
      await tester.enterText(find.byType(TextField), 'zoe');
      await tester.pumpAndSettle();
      expect(find.text('Zoe Mora Ruiz'), findsOneWidget);
      expect(find.text('Ana Pérez Ruiz'), findsNothing);

      await tester.enterText(find.byType(TextField), 'nadie');
      await tester.pumpAndSettle();
      expect(find.text('Sin resultados'), findsOneWidget);
      await tester.tap(find.text('Quitar filtros'));
      await tester.pumpAndSettle();
      expect(find.text('Ana Pérez Ruiz'), findsOneWidget);
    });

    testWidgets('sin otros clientes lo dice', (tester) async {
      final c = ClientesController(ClientesFalso(admin: true));
      await montar(
        tester,
        ClientesPage(
          controller: c,
          idSeleccionado: null,
          alSeleccionar: (_) {},
          alVolver: () {},
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Aún no hay otros clientes'), findsOneWidget);
    });

    testWidgets('un error de carga ofrece reintentar', (tester) async {
      final falso = ClientesFalso(admin: true)..errorListar = const ConexionException('sin red');
      final c = ClientesController(falso);
      await montar(
        tester,
        ClientesPage(
          controller: c,
          idSeleccionado: null,
          alSeleccionar: (_) {},
          alVolver: () {},
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('No pudimos cargar los clientes'), findsOneWidget);

      falso
        ..errorListar = null
        ..clientes = [clientePrueba(2, 'Zoe', 'Mora')];
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('Zoe Mora Ruiz'), findsOneWidget);
    });

    testWidgets('quien no es administrador ve un aviso y no se piden clientes', (tester) async {
      final (_, falso) = await abrir(tester, admin: false);
      expect(find.text('Solo para administradores'), findsOneWidget);
      expect(falso.listados, 0);
    });

    testWidgets('el detalle muestra todos los datos del cliente', (tester) async {
      await abrir(tester, id: 3);

      expect(find.text('Ana Pérez Ruiz'), findsWidgets);
      expect(find.text('Datos personales'), findsOneWidget);
      expect(find.text('CURP3'), findsOneWidget);
      expect(find.text('RFC3'), findsOneWidget);
      expect(find.text('Contacto'), findsOneWidget);
      expect(find.text('Empleo'), findsOneWidget);
      expect(find.text('Domicilio'), findsOneWidget);
      expect(find.text('Cuentas bancarias'), findsOneWidget);
      expect(find.text('000000000000000003'), findsOneWidget);
      expect(find.text('Reactivar cuenta'), findsOneWidget);
    });

    testWidgets('desactivar pide confirmación y cambia el estado', (tester) async {
      final (c, falso) = await abrir(tester, id: 2);

      await tester.tap(find.text('Desactivar cuenta'));
      await tester.pumpAndSettle();
      expect(find.text('¿Desactivar la cuenta de Zoe?'), findsOneWidget);

      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(falso.cambios, isEmpty);

      await tester.tap(find.text('Desactivar cuenta'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Desactivar'));
      await tester.pumpAndSettle();

      expect(falso.cambios, [(rfc: 'RFC2', activo: false)]);
      expect(c.porId(2)!.activo, isFalse);
      expect(find.text('Reactivar cuenta'), findsOneWidget);
      expect(find.text('Cuenta desactivada'), findsOneWidget);
      await drenarToasts(tester);
    });

    testWidgets('reactivar y los errores del servidor se avisan', (tester) async {
      final (_, falso) = await abrir(tester, id: 3);

      falso.errorCambiar = const ApiException(403, 'Sin permiso');
      await tester.tap(find.text('Reactivar cuenta'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reactivar'));
      await tester.pumpAndSettle();
      expect(find.text('Sin permiso'), findsOneWidget);
      expect(falso.cambios, isEmpty);
      await drenarToasts(tester);

      falso.errorCambiar = null;
      await tester.tap(find.text('Reactivar cuenta'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reactivar'));
      await tester.pumpAndSettle();
      expect(falso.cambios, [(rfc: 'RFC3', activo: true)]);
      expect(find.text('Desactivar cuenta'), findsOneWidget);
      await drenarToasts(tester);
    });

    testWidgets('un cliente que no existe lo dice y deja volver', (tester) async {
      await abrir(tester, id: 99);
      expect(find.text('No encontramos este cliente'), findsOneWidget);
    });
  });

  group('menú', () {
    Future<Dependencias> crear({required bool admin}) async {
      final pagos = PagosFalso()..alHistorial = (_) async => pagina([]);
      final deps = Dependencias.crear(
        autenticacion: GatewayFalso(),
        almacenSesion: AlmacenFalso()..guardada = sesionPrueba(),
        catalogoRepositorio: CatalogoFalso(() async => []),
        pagosRepositorio: pagos,
        cuentasRepositorio: CuentasFalso(() async => <Cuenta>[]),
        perfilRepositorio: PerfilFalso(),
        clientesRepositorio: ClientesFalso(
          admin: admin,
          clientes: [clientePrueba(2, 'Zoe', 'Mora')],
        ),
      );
      await deps.sesion.restaurar();
      return deps;
    }

    Future<void> abrir(
      WidgetTester tester,
      Dependencias deps,
      ConstructorShell shell,
      Size tamano,
    ) async {
      tester.view.physicalSize = tamano;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(GestoPagoApp(dependencias: deps, shell: shell));
      await tester.pumpAndSettle();
    }

    for (final (nombre, shell, tamano) in [
      ('barra lateral web', construirShellWeb, const Size(1300, 900)),
      ('barra inferior web', construirShellWeb, const Size(500, 900)),
      ('barra inferior móvil', construirShellMovil, const Size(500, 900)),
    ]) {
      testWidgets('el administrador ve Clientes en la $nombre', (tester) async {
        final deps = await crear(admin: true);
        await abrir(tester, deps, shell, tamano);

        await tester.tap(find.text('Clientes').first);
        await tester.pumpAndSettle();
        expect(find.text('Zoe Mora Ruiz'), findsOneWidget);

        await tester.tap(find.text('Zoe Mora Ruiz'));
        await tester.pumpAndSettle();
        expect(find.text('Desactivar cuenta'), findsOneWidget);
        expect(find.text('Cliente'), findsWidgets); // título de la pantalla de detalle

        await tester.pumpWidget(const SizedBox());
        deps.sesion.dispose();
      });

      testWidgets('quien no es administrador no ve Clientes en la $nombre', (tester) async {
        final deps = await crear(admin: false);
        await abrir(tester, deps, shell, tamano);
        expect(find.text('Clientes'), findsNothing);
        expect(find.text('Mi perfil'), findsWidgets); // el menú sigue con sus cuatro secciones

        await tester.pumpWidget(const SizedBox());
        deps.sesion.dispose();
      });
    }

    testWidgets('quien no es administrador no entra por la ruta directa', (tester) async {
      final deps = await crear(admin: false);
      await abrir(tester, deps, construirShellWeb, const Size(1300, 900));

      GoRouterHelper.ir(tester, '/clientes');
      await tester.pumpAndSettle();
      expect(find.text('Zoe Mora Ruiz'), findsNothing);
      expect(find.text('Buscar por servicio o producto'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
      deps.sesion.dispose();
    });
  });
}

/// Navega con el router de la app montada.
class GoRouterHelper {
  static void ir(WidgetTester tester, String ruta) =>
      GoRouter.of(tester.element(find.byType(Scaffold).first)).go(ruta);
}
