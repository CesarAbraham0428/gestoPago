import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/ui/widgets_estado.dart';
import 'package:frontend/pagos/application/historial_controller.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/infraestructure/ui/historial_page.dart';
import 'package:frontend/productos/application/catalogo_controller.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/infraestructure/ui/catalogo_page.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('CatalogoPage', () {
    final productos = [
      producto('Recarga 50', 'Telcel', 1),
      producto('Gift card', 'Amazon', 2, cat: 10),
      producto('Luz con comisión', 'CFE', 3, tipo: 2, precio: 8),
      producto('Luz sin comisión', 'CFE', 4, tipo: 2, precio: 0),
      producto('Solo consulta', 'Ñandú', 5, precio: null),
    ];

    Future<CatalogoController> abrir(
      WidgetTester tester, {
      String? clave,
      List<String>? seleccionados,
      Size tamano = const Size(500, 900),
      Future<List<Producto>> Function()? repo,
    }) async {
      final controller = CatalogoController(
        CatalogoFalso(repo ?? () async => productos),
      );
      await montar(
        tester,
        CatalogoPage(
          controller: controller,
          claveSeleccionada: clave,
          alSeleccionar: (c) => seleccionados?.add(c),
          construirAccionCompra: (_, p) => Text('COMPRAR ${p.clave}'),
        ),
        tamano: tamano,
      );
      return controller;
    }

    testWidgets('lista con encabezados, subtítulos y selección', (tester) async {
      final elegidos = <String>[];
      await abrir(tester, seleccionados: elegidos);
      expect(find.text('Cargando catálogo'), findsOneWidget);
      await tester.pumpAndSettle();
      expect(find.text('4 de 5 productos'), findsOneWidget);
      expect(find.text('Recarga de saldo'), findsOneWidget);
      expect(find.text('Código canjeable'), findsOneWidget);
      expect(find.textContaining('comisión \$8.00'), findsOneWidget);
      expect(find.textContaining('sin comisión'), findsWidgets);

      await tester.tap(find.text('Recarga 50'));
      expect(elegidos, ['1-1']);
    });

    testWidgets('mostrar también los no disponibles', (tester) async {
      await abrir(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.byType(Switch));
      await tester.pumpAndSettle();
      expect(find.text('Solo consulta'), findsOneWidget);
      expect(find.text('No disponible para compra'), findsOneWidget);
    });

    testWidgets('búsqueda, borrar y filtros por categoría', (tester) async {
      await abrir(tester);
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), 'telcel');
      await tester.pumpAndSettle();
      expect(find.text('1 de 5 productos'), findsOneWidget);
      await tester.tap(find.byTooltip('Borrar búsqueda'));
      await tester.pumpAndSettle();
      expect(find.text('4 de 5 productos'), findsOneWidget);

      await tester.tap(find.text('Recargas'));
      await tester.pumpAndSettle();
      expect(find.text('1 de 5 productos'), findsOneWidget);
      await tester.tap(find.text('Todo'));
      await tester.pumpAndSettle();
      expect(find.text('4 de 5 productos'), findsOneWidget);
    });

    testWidgets('sin resultados permite quitar los filtros', (tester) async {
      await abrir(tester);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'zzzz');
      await tester.pumpAndSettle();
      expect(find.text('Sin resultados'), findsOneWidget);
      await tester.tap(find.text('Quitar filtros'));
      await tester.pumpAndSettle();
      expect(find.text('Sin resultados'), findsNothing);
    });

    testWidgets('error al cargar y reintentar', (tester) async {
      var falla = true;
      await abrir(
        tester,
        repo: () async {
          if (falla) throw const ApiException(503, 'Catálogo caído');
          return productos;
        },
      );
      await tester.pumpAndSettle();
      expect(find.text('Catálogo caído'), findsOneWidget);
      falla = false;
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('4 de 5 productos'), findsOneWidget);
    });

    testWidgets('error sin mensaje usa el texto genérico', (tester) async {
      final controller = await abrir(tester, repo: () async => throw StateError('x'));
      await tester.pumpAndSettle();
      expect(controller.error, isNotNull);
      expect(find.text('No pudimos cargar el catálogo'), findsOneWidget);
    });

    testWidgets('jalar para actualizar recarga el catálogo', (tester) async {
      var cargas = 0;
      await abrir(
        tester,
        repo: () async {
          cargas++;
          return productos;
        },
      );
      await tester.pumpAndSettle();
      await tester.fling(find.text('Recarga 50'), const Offset(0, 400), 1000);
      await tester.pumpAndSettle();
      expect(cargas, 2);
    });

    testWidgets('detalle en pantalla angosta', (tester) async {
      await abrir(tester, clave: '1-1');
      await tester.pumpAndSettle();
      expect(find.text('COMPRAR 1-1'), findsOneWidget);
      expect(find.text('Buscar por servicio o producto'), findsNothing);
    });

    testWidgets('detalle de un producto inexistente', (tester) async {
      await abrir(tester, clave: '99-9');
      await tester.pumpAndSettle();
      expect(find.text('No encontramos este producto'), findsOneWidget);
    });

    testWidgets('detalle mientras el catálogo carga', (tester) async {
      final espera = Completer<List<Producto>>();
      await abrir(tester, clave: '1-1', repo: () => espera.future);
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      espera.complete(productos);
      await tester.pumpAndSettle();
      expect(find.text('COMPRAR 1-1'), findsOneWidget);
    });

    testWidgets('pantalla ancha: lista y detalle lado a lado', (tester) async {
      await abrir(tester, tamano: const Size(1300, 900));
      await tester.pumpAndSettle();
      expect(find.text('Elige un producto'), findsOneWidget);

      await abrir(tester, clave: '2-1', tamano: const Size(1300, 900));
      await tester.pumpAndSettle();
      expect(find.text('COMPRAR 2-1'), findsOneWidget);
      expect(find.text('Buscar por servicio o producto'), findsOneWidget);
    });
  });

  group('HistorialPage', () {
    Future<HistorialController> abrir(
      WidgetTester tester,
      PagosFalso pagos, {
      String? upc,
      List<String>? elegidos,
      Size tamano = const Size(500, 900),
    }) async {
      final controller = HistorialController(pagos);
      await montar(
        tester,
        HistorialPage(
          controller: controller,
          referenciaSeleccionada: upc,
          alSeleccionar: (u) => elegidos?.add(u),
        ),
        tamano: tamano,
      );
      return controller;
    }

    PagosFalso conCompras(List<CompraHistorial> items, {int total = 1}) =>
        PagosFalso()..alHistorial = (n) async => pagina(items, n: n, total: total);

    testWidgets('lista de compras y selección', (tester) async {
      final elegidos = <String>[];
      await abrir(
        tester,
        conCompras([compra('U1'), compra('U2', producto: null, servicio: null)]),
        elegidos: elegidos,
      );
      expect(find.text('Cargando tus pagos'), findsOneWidget);
      await tester.pumpAndSettle();
      expect(find.text('Recarga 50'), findsOneWidget);
      expect(find.text('Compra'), findsOneWidget);
      await tester.tap(find.text('Recarga 50'));
      expect(elegidos, ['U1']);
    });

    testWidgets('historial vacío', (tester) async {
      await abrir(tester, conCompras([]));
      await tester.pumpAndSettle();
      expect(find.text('Aún no tienes pagos'), findsOneWidget);
    });

    testWidgets('error con mensaje y sin él, y reintentar', (tester) async {
      var falla = true;
      final pagos = PagosFalso()
        ..alHistorial = (n) async {
          if (falla) throw const ApiException(500, 'Falla');
          return pagina([compra('U1')]);
        };
      await abrir(tester, pagos);
      await tester.pumpAndSettle();
      expect(find.text('No pudimos cargar tus pagos'), findsOneWidget);
      expect(find.text('Falla'), findsOneWidget);
      falla = false;
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('Recarga 50'), findsOneWidget);
    });

    testWidgets('ver más compras, con error y con carga en curso', (tester) async {
      final espera = Completer<Pagina<CompraHistorial>>();
      var intento = 0;
      final pagos = PagosFalso()
        ..alHistorial = (n) {
          if (n == 0) return Future.value(pagina([compra('U1')], total: 3));
          intento++;
          if (intento == 1) throw const ApiException(500, 'No hay más por ahora');
          return espera.future;
        };
      await abrir(tester, pagos);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Ver más compras'));
      await tester.pumpAndSettle();
      expect(find.text('No hay más por ahora'), findsOneWidget);

      await tester.tap(find.text('Ver más compras'));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      espera.complete(pagina([compra('U2')], n: 1, total: 3));
      await tester.pumpAndSettle();
      expect(find.text('Ver más compras'), findsOneWidget);
    });

    // La fila de filtros se desplaza en horizontal: se acerca el chip antes de tocarlo.
    Future<void> filtrarPor(WidgetTester tester, String etiqueta) async {
      final chip = find.widgetWithText(ChoiceChip, etiqueta);
      await tester.ensureVisible(chip);
      await tester.tap(chip);
    }

    testWidgets('lista todos los estados y filtra por estado', (tester) async {
      await abrir(
        tester,
        conCompras([
          compra('U1', producto: 'Pago uno'),
          compra('U2', producto: 'Pago dos', estado: 'RECHAZADO'),
          compra('U3', producto: 'Pago tres', estado: 'EN_REVISION'),
          compra('U4', producto: 'Pago cuatro', estado: 'PENDIENTE'),
        ]),
      );
      await tester.pumpAndSettle();
      expect(find.text('Aprobado'), findsOneWidget);
      expect(find.text('Rechazado'), findsOneWidget);
      expect(find.text('En proceso'), findsNWidgets(3)); // dos filas y el filtro

      await filtrarPor(tester, 'Rechazados');
      await tester.pumpAndSettle();
      expect(find.text('Pago dos'), findsOneWidget);
      expect(find.text('Pago uno'), findsNothing);

      await tester.tap(find.widgetWithText(ChoiceChip, 'En proceso'));
      await tester.pumpAndSettle();
      expect(find.text('Pago tres'), findsOneWidget);
      expect(find.text('Pago cuatro'), findsOneWidget);

      await filtrarPor(tester, 'Aprobados');
      await tester.pumpAndSettle();
      expect(find.text('Pago uno'), findsOneWidget);
      expect(find.text('Pago dos'), findsNothing);

      await filtrarPor(tester, 'Todos');
      await tester.pumpAndSettle();
      expect(find.text('Pago dos'), findsOneWidget);
    });

    testWidgets('filtro sin coincidencias avisa', (tester) async {
      await abrir(tester, conCompras([compra('U1')]));
      await tester.pumpAndSettle();
      await filtrarPor(tester, 'Rechazados');
      await tester.pumpAndSettle();
      expect(find.text('Sin pagos con este estado'), findsOneWidget);
    });

    testWidgets('detalle de un pago rechazado dice que fue rechazado', (tester) async {
      await abrir(
        tester,
        conCompras([compra('U1', estado: 'RECHAZADO')]),
        upc: 'U1',
      );
      await tester.pumpAndSettle();
      expect(find.text('Pago rechazado'), findsOneWidget);
      expect(find.text('Compra confirmada'), findsNothing);
    });

    group('buscador por UPC', () {
      const upc = 'ABCDE12345FGHIJ';

      Future<PagosFalso> buscar(
        WidgetTester tester,
        Future<EstadoPago> Function(String) alConsultar, {
        List<CompraHistorial> lista = const [],
        List<String>? elegidos,
      }) async {
        final pagos = conCompras(lista)..alConsultar = alConsultar;
        await abrir(tester, pagos, elegidos: elegidos);
        await tester.pumpAndSettle();
        return pagos;
      }

      Future<void> escribirYBuscar(WidgetTester tester, String texto) async {
        await tester.enterText(find.byType(TextField), texto);
        await tester.tap(find.byTooltip('Buscar pago'));
        await tester.pumpAndSettle();
      }

      testWidgets('consulta el pago y muestra su estado', (tester) async {
        String? consultado;
        await buscar(tester, (u) async {
          consultado = u;
          return EstadoPago(
            upc: u,
            estado: 'RECHAZADO',
            mensaje: 'El pago no se realizó; no se realizó ningún cargo',
            monto: 100,
            comision: 5,
            referencia: 'REF9',
          );
        });
        await escribirYBuscar(tester, upc.toLowerCase());
        expect(consultado, upc);
        expect(find.text('Rechazado'), findsOneWidget);
        expect(find.text('El pago no se realizó; no se realizó ningún cargo'), findsOneWidget);
        expect(find.widgetWithText(FilaDato, upc), findsOneWidget);
        expect(find.text('REF9'), findsOneWidget);
        expect(find.text('Comisión'), findsOneWidget);
        expect(find.text('Ver recibo'), findsNothing);
      });

      testWidgets('un pago aprobado ya cargado ofrece ver el recibo', (tester) async {
        final elegidos = <String>[];
        await buscar(
          tester,
          (u) async => EstadoPago(
            upc: u,
            estado: 'APROBADO',
            mensaje: 'El pago fue aplicado correctamente',
            monto: 50,
            idTx: 5,
            numAutorizacion: 'AUTX',
          ),
          lista: [compra(upc)],
          elegidos: elegidos,
        );
        await escribirYBuscar(tester, upc);
        expect(find.text('Aprobado'), findsOneWidget);
        expect(find.text('AUTX'), findsOneWidget);
        await tester.tap(find.text('Ver recibo'));
        expect(elegidos, [upc]);
      });

      testWidgets('rechaza un UPC mal formado sin consultar', (tester) async {
        var consultas = 0;
        await buscar(tester, (u) async {
          consultas++;
          throw StateError('no debe llamarse');
        });
        await escribirYBuscar(tester, 'ABC');
        expect(find.text('El UPC tiene 15 letras o números.'), findsOneWidget);
        expect(consultas, 0);
      });

      testWidgets('muestra el error del servicio y se puede limpiar', (tester) async {
        await buscar(tester, (u) async => throw const ApiException(404, 'No existe un pago con ese UPC'));
        await escribirYBuscar(tester, upc);
        expect(find.text('No existe un pago con ese UPC'), findsOneWidget);
        await tester.tap(find.byTooltip('Limpiar búsqueda'));
        await tester.pumpAndSettle();
        expect(find.text('No existe un pago con ese UPC'), findsNothing);
        expect(find.byTooltip('Buscar pago'), findsOneWidget);
      });

      testWidgets('error inesperado y búsqueda con Enter', (tester) async {
        await buscar(tester, (u) async => throw StateError('boom'));
        await tester.enterText(find.byType(TextField), upc);
        await tester.testTextInput.receiveAction(TextInputAction.search);
        await tester.pumpAndSettle();
        expect(find.text('No pudimos consultar el pago. Inténtalo de nuevo.'), findsOneWidget);
      });

      testWidgets('limpiar mientras consulta descarta la respuesta tardía', (tester) async {
        final espera = Completer<EstadoPago>();
        await buscar(tester, (u) => espera.future);
        await tester.enterText(find.byType(TextField), upc);
        await tester.tap(find.byTooltip('Buscar pago'));
        await tester.pump();
        expect(find.text('Consultando el pago'), findsOneWidget);
        await tester.tap(find.byTooltip('Limpiar búsqueda'));
        await tester.pump();
        espera.complete(const EstadoPago(upc: upc, estado: 'APROBADO', mensaje: 'tardío'));
        await tester.pumpAndSettle();
        expect(find.text('tardío'), findsNothing);
      });
    });

    testWidgets('jalar para actualizar', (tester) async {
      final pagos = conCompras([compra('U1')]);
      await abrir(tester, pagos);
      await tester.pumpAndSettle();
      await tester.fling(find.text('Recarga 50'), const Offset(0, 400), 1000);
      await tester.pumpAndSettle();
      expect(pagos.historiales, 2);
    });

    testWidgets('detalle en pantalla angosta', (tester) async {
      await abrir(tester, conCompras([compra('U1', telefono: '5512345678')]), upc: 'U1');
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
      expect(find.text('55 1234 5678'), findsOneWidget);
    });

    testWidgets('detalle inexistente y detalle mientras carga', (tester) async {
      await abrir(tester, conCompras([compra('U1')]), upc: 'NOEXISTE');
      await tester.pumpAndSettle();
      expect(find.text('No encontramos esta compra'), findsOneWidget);

      final espera = Completer<Pagina<CompraHistorial>>();
      final lento = PagosFalso()..alHistorial = (n) => espera.future;
      await abrir(tester, lento, upc: 'U1');
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      espera.complete(pagina([compra('U1')]));
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
    });

    testWidgets('pantalla ancha: lista y recibo lado a lado', (tester) async {
      await abrir(
        tester,
        conCompras([compra('U1')]),
        tamano: const Size(1300, 900),
      );
      await tester.pumpAndSettle();
      expect(find.text('Elige una compra'), findsOneWidget);

      await abrir(
        tester,
        conCompras([compra('U1')]),
        upc: 'U1',
        tamano: const Size(1300, 900),
      );
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
    });

    testWidgets('no vuelve a cargar si el controlador ya tiene datos', (tester) async {
      final pagos = conCompras([compra('U1')]);
      final controller = HistorialController(pagos);
      await controller.recargar();
      await montar(
        tester,
        HistorialPage(
          controller: controller,
          referenciaSeleccionada: null,
          alSeleccionar: (_) {},
        ),
      );
      await tester.pumpAndSettle();
      expect(pagos.historiales, 1);
    });
  });
}
