import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:frontend/authentication/domain/entities/evento_sesion.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/notificaciones/application/notificaciones_controller.dart';
import 'package:frontend/notificaciones/domain/entities/notificacion.dart';
import 'package:frontend/notificaciones/domain/ports/notificaciones_repository.dart';
import 'package:frontend/notificaciones/infraestructure/adapters/api_notificaciones_repository.dart';
import 'package:frontend/notificaciones/infraestructure/ui/notificaciones_page.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

Map<String, dynamic> _json(
  int id, {
  String tipo = 'PAGO_APROBADO',
  bool leida = false,
  String? upc = 'UPC000000000001',
  DateTime? fecha,
}) => {
  'id': id,
  'tipo': tipo,
  'titulo': 'Pago aprobado',
  'mensaje': 'Se cobraron \$100.00 de tu cuenta.',
  'upc': upc,
  'leida': leida,
  'fecha': (fecha ?? DateTime.now()).toUtc().toIso8601String(),
};

Notificacion _n(int id, {bool leida = false}) =>
    Notificacion.desdeJson(_json(id, leida: leida))!;

class _RepoFalso implements NotificacionesRepository {
  List<Notificacion> lista = [];
  int noLeidas = 0;
  int totalPaginas = 1;
  bool falla = false;
  final leidas = <int>[];
  int todasLeidas = 0;
  int consultas = 0;

  @override
  Future<PaginaNotificaciones> listar({
    required int pagina,
    required int tamanio,
  }) async {
    consultas++;
    if (falla) throw const ConexionException();
    return PaginaNotificaciones(
      elementos: lista,
      pagina: pagina,
      totalPaginas: totalPaginas,
      noLeidas: noLeidas,
    );
  }

  @override
  Future<void> marcarLeida(int id) async {
    if (falla) throw const ConexionException();
    leidas.add(id);
  }

  @override
  Future<void> marcarTodasLeidas() async {
    if (falla) throw const ConexionException();
    todasLeidas++;
  }
}

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('Notificacion.desdeJson', () {
    test('interpreta el objeto del servidor', () {
      final n = Notificacion.desdeJson(_json(5, tipo: 'PAGO_RECHAZADO'))!;
      expect(n.id, 5);
      expect(n.tipo, TipoNotificacion.pagoRechazado);
      expect(n.upc, 'UPC000000000001');
      expect(n.leida, isFalse);
      expect(n.marcadaLeida().leida, isTrue);
    });

    test('un tipo desconocido es "otro" y lo incompleto se descarta', () {
      expect(
        Notificacion.desdeJson(_json(1, tipo: 'NUEVO'))!.tipo,
        TipoNotificacion.otro,
      );
      expect(Notificacion.desdeJson(null), isNull);
      expect(Notificacion.desdeJson({'id': 1}), isNull);
    });
  });

  group('ApiNotificacionesRepository', () {
    test('lista con paginación y total sin leer', () async {
      late http.BaseRequest recibida;
      final cliente = MockClient((r) async {
        recibida = r;
        return http.Response(
          jsonEncode({
            'codigo': 0,
            'mensaje': 'ok',
            'datos': {
              'lista': {
                'datos': [
                  _json(2),
                  {'basura': true},
                ],
                'pagina': 0,
                'totalPaginas': 3,
              },
              'noLeidas': 4,
            },
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });
      final repo = ApiNotificacionesRepository(
        ApiClient(baseUrl: 'http://localhost', client: cliente),
      );

      final pagina = await repo.listar(pagina: 0, tamanio: 20);

      expect(recibida.method, 'POST');
      expect(recibida.url.path, '/notificaciones/consultar');
      expect(jsonDecode((recibida as http.Request).body), {
        'pagina': 0,
        'tamanio': 20,
      });
      expect(pagina.elementos.map((n) => n.id), [2]);
      expect(pagina.noLeidas, 4);
      expect(pagina.hayMas, isTrue);
    });

    test('marca una o todas como leídas', () async {
      final llamadas = <String>[];
      final cliente = MockClient((r) async {
        llamadas.add('${r.method} ${r.url.path}');
        return http.Response(
          jsonEncode({'codigo': 0, 'mensaje': 'ok', 'datos': null}),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });
      final repo = ApiNotificacionesRepository(
        ApiClient(baseUrl: 'http://localhost', client: cliente),
      );

      await repo.marcarLeida(9);
      await repo.marcarTodasLeidas();

      expect(llamadas, [
        'POST /notificaciones/leer',
        'POST /notificaciones/leer-todas',
      ]);
    });
  });

  group('NotificacionesController', () {
    late _RepoFalso repo;
    late StreamController<EventoSesion> canal;
    late NotificacionesController controller;

    setUp(() {
      repo = _RepoFalso();
      canal = StreamController<EventoSesion>.broadcast();
      controller = NotificacionesController(repo, eventos: canal.stream);
    });
    tearDown(() {
      controller.dispose();
      canal.close();
    });

    test('carga la primera página y el total sin leer', () async {
      repo.lista = [_n(2), _n(1, leida: true)];
      repo.noLeidas = 1;

      await controller.recargar();

      expect(controller.estado, EstadoNotificaciones.listo);
      expect(controller.items.map((n) => n.id), [2, 1]);
      expect(controller.noLeidas, 1);
    });

    test('un fallo deja el estado de error con un mensaje', () async {
      repo.falla = true;

      await controller.recargar();

      expect(controller.estado, EstadoNotificaciones.error);
      expect(controller.error, isNotNull);
    });

    test('cargar más agrega la siguiente página sin duplicar', () async {
      repo.lista = [_n(2)];
      repo.totalPaginas = 2;
      await controller.recargar();
      expect(controller.hayMas, isTrue);

      repo.lista = [_n(2), _n(1)];
      await controller.cargarMas();

      expect(controller.items.map((n) => n.id), [2, 1]);
    });

    test(
      'un evento notificacion entra al principio, suma y se anuncia',
      () async {
        repo.lista = [_n(1, leida: true)];
        await controller.recargar();
        final anunciadas = <Notificacion>[];
        controller.nuevas.listen(anunciadas.add);

        canal.add((nombre: 'notificacion', datos: jsonEncode(_json(7))));
        await Future<void>.delayed(Duration.zero);

        expect(controller.items.first.id, 7);
        expect(controller.noLeidas, 1);
        expect(anunciadas.single.id, 7);

        // Repetir el mismo aviso (p. ej. tras reconectar) no lo duplica.
        canal.add((nombre: 'notificacion', datos: jsonEncode(_json(7))));
        await Future<void>.delayed(Duration.zero);
        expect(controller.items.where((n) => n.id == 7), hasLength(1));
        expect(controller.noLeidas, 1);
      },
    );

    test('datos inválidos en el evento se ignoran', () async {
      canal.add((nombre: 'notificacion', datos: 'no es json'));
      await Future<void>.delayed(Duration.zero);
      expect(controller.items, isEmpty);
    });

    test('el evento conectado recarga la bandeja', () async {
      repo.lista = [_n(3)];
      repo.noLeidas = 1;

      canal.add((nombre: 'conectado', datos: 'ok'));
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(repo.consultas, 1);
      expect(controller.noLeidas, 1);
    });

    test(
      'marcar como leída actualiza al instante y avisa al servidor',
      () async {
        repo.lista = [_n(2), _n(1)];
        repo.noLeidas = 2;
        await controller.recargar();

        await controller.marcarLeida(controller.items.first);

        expect(controller.items.first.leida, isTrue);
        expect(controller.noLeidas, 1);
        expect(repo.leidas, [2]);

        // Ya leída: no vuelve a llamar al servidor.
        await controller.marcarLeida(controller.items.first);
        expect(repo.leidas, [2]);
      },
    );

    test('marcar todas como leídas', () async {
      repo.lista = [_n(2), _n(1)];
      repo.noLeidas = 2;
      await controller.recargar();

      await controller.marcarTodasLeidas();

      expect(controller.noLeidas, 0);
      expect(controller.items.every((n) => n.leida), isTrue);
      expect(repo.todasLeidas, 1);
    });

    test('si el servidor falla al marcar, se vuelve a consultar', () async {
      repo.lista = [_n(1)];
      repo.noLeidas = 1;
      await controller.recargar();
      repo.falla = true;

      await controller.marcarLeida(controller.items.first);
      await Future<void>.delayed(Duration.zero);

      expect(repo.consultas, greaterThan(1));
    });

    test('el filtro muestra solo las sin leer y limpiar vacía todo', () async {
      repo.lista = [_n(2), _n(1, leida: true)];
      repo.noLeidas = 1;
      await controller.recargar();

      controller.filtrarNoLeidas(true);
      expect(controller.visibles.map((n) => n.id), [2]);

      controller.limpiar();
      expect(controller.items, isEmpty);
      expect(controller.noLeidas, 0);
      expect(controller.soloNoLeidas, isFalse);
      expect(controller.estado, EstadoNotificaciones.inicial);
    });
  });

  group('NotificacionesPage', () {
    late _RepoFalso repo;
    late NotificacionesController controller;

    Future<void> montar(
      WidgetTester tester, {
      ValueChanged<String>? alVer,
    }) async {
      tester.view.physicalSize = const Size(900, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NotificacionesPage(
              controller: controller,
              alVerCompra: alVer ?? (_) {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    setUp(() {
      repo = _RepoFalso();
      controller = NotificacionesController(repo);
    });
    tearDown(() => controller.dispose());

    testWidgets('sin avisos muestra el estado vacío', (tester) async {
      await montar(tester);

      expect(find.text('Todo en calma'), findsOneWidget);
      expect(find.text('Estás al día'), findsOneWidget); // resumen
    });

    testWidgets('lista los avisos, marca uno como leído y navega a la compra', (
      tester,
    ) async {
      repo.lista = [
        Notificacion.desdeJson(_json(2, tipo: 'PAGO_RECHAZADO'))!,
        Notificacion.desdeJson(
          _json(
            1,
            leida: true,
            fecha: DateTime.now().subtract(const Duration(days: 1)),
          ),
        )!,
      ];
      repo.noLeidas = 1;
      String? abierta;

      await montar(tester, alVer: (u) => abierta = u);

      expect(find.text('Tienes 1 aviso sin leer'), findsOneWidget);
      expect(find.text('HOY'), findsOneWidget);
      expect(find.text('AYER'), findsOneWidget);
      expect(find.text('Ver compra'), findsNWidgets(2));

      await tester.tap(find.text('Ver compra').first);
      await tester.pumpAndSettle();

      expect(abierta, 'UPC000000000001');
      expect(repo.leidas, [2]);
      expect(find.text('Estás al día'), findsWidgets);
    });

    testWidgets('marcar todo como leído y filtro de sin leer', (tester) async {
      repo.lista = [_n(2), _n(1)];
      repo.noLeidas = 2;
      await montar(tester);

      await tester.tap(find.text('Sin leer (2)'));
      await tester.pumpAndSettle();
      expect(find.text('Pago aprobado'), findsNWidgets(2));

      await tester.tap(find.text('Marcar todo como leído'));
      await tester.pumpAndSettle();

      expect(repo.todasLeidas, 1);
      expect(find.text('No tienes notificaciones sin leer.'), findsOneWidget);
    });

    testWidgets('un fallo de carga permite reintentar', (tester) async {
      repo.falla = true;
      await montar(tester);
      expect(find.text('No pudimos cargar tus notificaciones'), findsOneWidget);

      repo.falla = false;
      repo.lista = [_n(1)];
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();

      expect(find.text('Pago aprobado'), findsOneWidget);
    });
  });
}
