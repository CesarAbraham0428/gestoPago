import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/infraestructure/ui/guardia_inactividad.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/ui/fondo_acceso.dart';
import 'package:frontend/core/ui/ilustracion.dart';
import 'package:frontend/core/ui/tarjeta_debito.dart';
import 'package:frontend/core/ui/widgets_estado.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/infraestructure/ui/recibo.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/infraestructure/ui/detalle_producto.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('widgets de estado', () {
    testWidgets('EtiquetaEstado con y sin icono', (tester) async {
      await montar(
        tester,
        const Column(
          children: [
            EtiquetaEstado(texto: 'Con icono', tono: Tono.exito, icono: Icons.check),
            EtiquetaEstado(texto: 'Sin icono', tono: Tono.neutro),
          ],
        ),
      );
      expect(find.text('Con icono'), findsOneWidget);
      expect(find.text('Sin icono'), findsOneWidget);
      expect(find.byIcon(Icons.check), findsOneWidget);
    });

    testWidgets('VistaCargando con y sin mensaje', (tester) async {
      await montar(tester, const VistaCargando());
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byType(Text), findsNothing);
      await montar(tester, const VistaCargando(mensaje: 'Un momento'));
      expect(find.text('Un momento'), findsOneWidget);
    });

    testWidgets('VistaMensaje con acción por omisión y personalizada', (tester) async {
      var toques = 0;
      await montar(
        tester,
        VistaMensaje(titulo: 'T', detalle: 'D', accion: () => toques++),
      );
      await tester.tap(find.text('Reintentar'));
      expect(toques, 1);
      await montar(
        tester,
        VistaMensaje(
          titulo: 'T',
          detalle: 'D',
          accion: () => toques++,
          etiquetaAccion: 'Otra',
        ),
      );
      expect(find.text('Otra'), findsOneWidget);
      await montar(tester, const VistaMensaje(titulo: 'T', detalle: 'D'));
      expect(find.byType(OutlinedButton), findsNothing);
    });

    testWidgets('FilaDato normal y destacada', (tester) async {
      await montar(
        tester,
        const Column(
          children: [
            FilaDato(etiqueta: 'A', valor: '1'),
            FilaDato(etiqueta: 'B', valor: '2', destacado: true),
          ],
        ),
      );
      expect(find.text('A'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);
    });

    testWidgets('AvisoEstado con y sin título y acción', (tester) async {
      await montar(
        tester,
        Column(
          children: [
            const AvisoEstado(tono: Tono.aviso, icono: Icons.info, texto: 'Solo texto'),
            AvisoEstado(
              tono: Tono.rechazo,
              icono: Icons.error,
              titulo: 'Título',
              texto: 'Detalle',
              accion: TextButton(onPressed: () {}, child: const Text('Hacer')),
            ),
          ],
        ),
      );
      expect(find.text('Solo texto'), findsOneWidget);
      expect(find.text('Título'), findsOneWidget);
      expect(find.text('Detalle'), findsOneWidget);
      expect(find.text('Hacer'), findsOneWidget);
    });
  });

  group('GuardiaInactividad', () {
    Future<SesionController> sesionActiva() async {
      final sesion = SesionController(
        gateway: GatewayFalso(),
        almacen: AlmacenFalso(),
      );
      await sesion.verificarCodigo(desafio: desafioPrueba, codigo: '123456');
      return sesion;
    }

    testWidgets('cierra la sesión tras el límite sin interacción', (tester) async {
      final sesion = await sesionActiva();
      addTearDown(sesion.dispose);
      await montar(
        tester,
        GuardiaInactividad(
          sesion: sesion,
          limite: const Duration(minutes: 1),
          child: const Text('contenido'),
        ),
      );
      await tester.pump(const Duration(seconds: 61));
      expect(sesion.autenticado, isFalse);
      expect(sesion.aviso, contains('inactividad'));
    });

    testWidgets('toques, desplazamiento, rueda y teclado reinician la cuenta', (tester) async {
      final sesion = await sesionActiva();
      addTearDown(sesion.dispose);
      await montar(
        tester,
        GuardiaInactividad(
          sesion: sesion,
          limite: const Duration(minutes: 1),
          child: const SizedBox.expand(child: ColoredBox(color: Colors.white)),
        ),
      );
      final centro = tester.getCenter(find.byType(ColoredBox).last);

      await tester.pump(const Duration(seconds: 40));
      await tester.tapAt(centro);
      await tester.pump(const Duration(seconds: 40));
      expect(sesion.autenticado, isTrue);

      final gesto = await tester.startGesture(centro);
      await gesto.moveBy(const Offset(10, 0));
      await gesto.up();
      await tester.pump(const Duration(seconds: 40));
      expect(sesion.autenticado, isTrue);

      final raton = TestPointer(1, PointerDeviceKind.mouse)..hover(centro);
      await tester.sendEventToBinding(raton.scroll(const Offset(0, 10)));
      await tester.pump(const Duration(seconds: 40));
      expect(sesion.autenticado, isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.keyA);
      await tester.pump(const Duration(seconds: 40));
      expect(sesion.autenticado, isTrue);

      await tester.pump(const Duration(seconds: 30));
      expect(sesion.autenticado, isFalse);
    });

    testWidgets('sin sesión no vigila', (tester) async {
      final sesion = SesionController(
        gateway: GatewayFalso(),
        almacen: AlmacenFalso(),
      );
      addTearDown(sesion.dispose);
      await montar(
        tester,
        GuardiaInactividad(
          sesion: sesion,
          limite: const Duration(seconds: 1),
          child: const SizedBox(),
        ),
      );
      await tester.pump(const Duration(seconds: 5));
      expect(sesion.autenticado, isFalse);
    });
  });

  group('Recibo', () {
    final copiados = <String>[];

    setUp(() {
      copiados.clear();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, (llamada) async {
            if (llamada.method == 'Clipboard.setData') {
              copiados.add((llamada.arguments as Map)['text'] as String);
            }
            return null;
          });
    });

    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, null);
    });

    Comprobante comprobante({
      String? referencia,
      String? codigo,
      String? leyenda,
      double comision = 0,
      double? saldo,
    }) => Comprobante(
      estado: 'APROBADO',
      monto: 50,
      comision: comision,
      registradoEn: DateTime(2026, 3, 5, 14, 30),
      idTx: 12,
      numAutorizacion: 'AUT',
      referencia: referencia,
      saldoDisponible: saldo,
      codigoCanje: codigo,
      leyenda: leyenda,
    );

    SolicitudCompra solicitud({String? telefono, String? referencia}) =>
        SolicitudCompra(
          producto: producto('Recarga 50', 'Telcel', 1),
          numeroCuenta: '123456789012345678',
          monto: 50,
          codigoOtp: '123456',
          telefono: telefono,
          referencia: referencia,
        );

    testWidgets('recarga con código de canje, saldo y comisión', (tester) async {
      final datos = DatosRecibo.deComprobante(
        comprobante(codigo: 'ABC-123', leyenda: 'Canjéalo en tienda', comision: 3, saldo: 900),
        solicitud(telefono: '5512345678'),
      );
      await montar(tester, SingleChildScrollView(child: Recibo(datos: datos)));
      expect(find.text('Compra confirmada'), findsOneWidget);
      expect(find.text('55 1234 5678'), findsOneWidget);
      expect(find.text('Comisión'), findsOneWidget);
      expect(find.text('Saldo disponible'), findsOneWidget);
      expect(find.text('Código para canjear'), findsOneWidget);
      expect(find.text('Canjéalo en tienda'), findsOneWidget);
      expect(find.text('AUT'), findsOneWidget);

      await tester.tap(find.byTooltip('Copiar código'));
      expect(copiados, ['ABC-123']);

      // Reconstruir el recibo reevalúa los pintores y recortes (shouldRepaint/shouldReclip).
      Widget app(String titulo) => MaterialApp(
        key: const ValueKey('app'),
        theme: AppTheme.light(),
        home: Scaffold(
          body: SingleChildScrollView(child: Recibo(datos: datos, titulo: titulo)),
        ),
      );
      await tester.pumpWidget(app('Uno'));
      await tester.pumpWidget(app('Otro'));
      expect(find.text('Otro'), findsOneWidget);

      // La línea punteada es const: se fuerza su reconstrucción a mano.
      final perforacion = find.byWidgetPredicate(
        (w) => w.runtimeType.toString() == '_Perforacion',
      );
      for (final e in perforacion.evaluate()) {
        e.markNeedsBuild();
      }
      await tester.pump();
    });

    testWidgets('pago de servicio usa la referencia del comprobante o la de la solicitud', (tester) async {
      final conComprobante = DatosRecibo.deComprobante(
        comprobante(referencia: 'REFCOMP'),
        solicitud(referencia: 'REFSOL'),
      );
      expect(conComprobante.destino, 'REFCOMP');
      expect(conComprobante.destinoEtiqueta, 'Referencia de pago');
      final conSolicitud = DatosRecibo.deComprobante(
        comprobante(),
        solicitud(referencia: 'REFSOL'),
      );
      expect(conSolicitud.destino, 'REFSOL');
      await montar(tester, SingleChildScrollView(child: Recibo(datos: conSolicitud)));
      expect(find.text('REFSOL'), findsOneWidget);
      expect(find.text('Comisión'), findsNothing);
      expect(find.text('Código para canjear'), findsNothing);
    });

    testWidgets('historial: referencia copiable con aviso', (tester) async {
      final datos = DatosRecibo.deHistorial(
        compra('UPC123456789012', telefono: '5512345678'),
      );
      expect(datos.destinoEtiqueta, 'Celular');
      await montar(tester, SingleChildScrollView(child: Recibo(datos: datos)));
      await tester.tap(find.byTooltip('Copiar referencia'));
      await tester.pump();
      expect(copiados, ['UPC123456789012']);
      expect(find.text('Referencia copiada'), findsOneWidget);
    });

    test('historial: etiquetas del destino', () {
      final porReferencia = DatosRecibo.deHistorial(compra('U', referencia: 'RR'));
      expect(porReferencia.destinoEtiqueta, 'Referencia de pago');
      expect(porReferencia.destino, 'RR');
      final sinDestino = DatosRecibo.deHistorial(compra('U', producto: null, servicio: null));
      expect(sinDestino.destinoEtiqueta, isNull);
      expect(sinDestino.titulo, 'Compra');
      final telefonoCorto = DatosRecibo.deHistorial(compra('U', telefono: '123'));
      expect(telefonoCorto.destinoEtiqueta, isNull);
    });

    testWidgets('sin destino, servicio, ni código no muestra esas filas', (tester) async {
      final datos = DatosRecibo(
        titulo: 'X',
        servicio: null,
        fecha: DateTime(2026),
        monto: 1,
        comision: 0,
        total: 1,
        codigoCanje: '',
      );
      await montar(tester, SingleChildScrollView(child: Recibo(datos: datos)));
      expect(find.text('Código para canjear'), findsNothing);
      expect(find.text('Cuenta'), findsNothing);
    });
  });

  group('DetalleProducto', () {
    Future<void> detalle(WidgetTester tester, Producto p) async {
      await montar(
        tester,
        SingleChildScrollView(
          child: DetalleProducto(producto: p, accionCompra: const Text('ACCION')),
        ),
      );
    }

    testWidgets('recarga de saldo', (tester) async {
      await detalle(tester, producto('Recarga 50', 'Telcel', 1, leyenda: 'Vigencia 30 días'));
      expect(find.text('Disponible para compra'), findsOneWidget);
      expect(find.text('Monto fijo'), findsOneWidget);
      expect(find.textContaining('número de celular'), findsOneWidget);
      expect(find.text('Vigencia 30 días'), findsOneWidget);
      expect(find.text('ACCION'), findsOneWidget);
    });

    testWidgets('código canjeable', (tester) async {
      await detalle(tester, producto('Gift', 'Amazon', 2, cat: 10, leyenda: ''));
      expect(find.textContaining('código para canjear'), findsOneWidget);
    });

    testWidgets('pago de servicio con y sin comisión', (tester) async {
      await detalle(tester, producto('Luz', 'CFE', 3, tipo: 2, precio: 8));
      expect(find.text('Tú indicas el monto a pagar'), findsOneWidget);
      expect(find.textContaining('comisión se suma'), findsOneWidget);
      await detalle(tester, producto('Luz', 'CFE', 3, tipo: 2, precio: 0));
      expect(find.text('Sin comisión'), findsOneWidget);
      expect(find.textContaining('comisión se suma'), findsNothing);
    });

    testWidgets('producto no disponible', (tester) async {
      await detalle(tester, producto('Info', 'X', 4, precio: null));
      expect(find.text('No disponible para compra'), findsOneWidget);
      expect(find.textContaining('consulta y no se puede comprar'), findsOneWidget);
      expect(find.text('ACCION'), findsNothing);
      expect(find.text('Este producto no se puede pagar'), findsOneWidget);
    });
  });

  testWidgets('los dibujos decorativos solo se repintan si cambian sus colores', (tester) async {
    await montar(
      tester,
      FondoAcceso(
        child: ListView(
          children: const [
            Ilustracion(icono: Icons.star, color: AppColors.azul, fondo: AppColors.turquesaTenue),
            TarjetaDebito(numeroCuenta: '123456789012345678', saldo: 10),
          ],
        ),
      ),
    );
    final pintores = {
      for (final p in tester.widgetList<CustomPaint>(find.byType(CustomPaint)).map((w) => w.painter).nonNulls)
        p.runtimeType.toString(): p,
    };

    for (final nombre in ['_FondoDecorado', '_Fondo', '_LineasChip']) {
      final pintor = pintores[nombre];
      expect(pintor, isNotNull, reason: nombre);
      expect(pintor!.shouldRepaint(pintor), isFalse, reason: nombre);
    }
  });
}
