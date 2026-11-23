import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/pagos/application/realizar_compra.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/infraestructure/ui/panel_compra.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

const _cuenta1 = Cuenta(id: 1, numeroCuenta: '123456789012345678', saldo: 1000, activa: true);
const _cuenta2 = Cuenta(id: 2, numeroCuenta: '999988887777666655', saldo: 20, activa: true);

class _Entorno {
  _Entorno(this.cuentas, this.pagos);
  final CuentasFalso cuentas;
  final PagosFalso pagos;
  int confirmadas = 0;
  int verHistorial = 0;
}

Comprobante _aprobado({double monto = 50}) => Comprobante(
  estado: 'APROBADO',
  monto: monto,
  comision: 0,
  registradoEn: DateTime(2026, 3, 5, 14, 30),
  idTx: 5,
  numAutorizacion: 'AUT',
);

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  final recarga = producto('Recarga 50', 'Telcel', 1);
  final servicio = producto('Luz', 'CFE', 3, tipo: 2, precio: 8, tipoReferencia: 'Número de servicio');

  Future<_Entorno> abrir(
    WidgetTester tester,
    Producto p, {
    Future<List<Cuenta>> Function()? cuentas,
    PagosFalso? pagos,
  }) async {
    final repoPagos = pagos ?? (PagosFalso()..alPagar = (_) async => _aprobado());
    final entorno = _Entorno(
      CuentasFalso(cuentas ?? () async => [_cuenta1]),
      repoPagos,
    );
    await montar(
      tester,
      SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: PanelCompra(
          producto: p,
          cuentas: entorno.cuentas,
          pagos: repoPagos,
          realizarCompra: RealizarCompra(repoPagos),
          alConfirmarCompra: () => entorno.confirmadas++,
          alVerHistorial: () => entorno.verHistorial++,
        ),
      ),
      tamano: const Size(500, 1400),
    );
    return entorno;
  }

  Finder campo(String etiqueta) => find.widgetWithText(TextFormField, etiqueta);

  Future<void> ponerCelular(WidgetTester tester) =>
      tester.enterText(campo('Número de celular'), '5512345678');

  Future<void> revisar(WidgetTester tester) async {
    await tester.tap(find.text('Realizar compra'));
    await tester.pumpAndSettle();
  }

  Future<void> pagarConPin(WidgetTester tester, [String pin = '1234']) async {
    await tester.enterText(campo('PIN de autorización'), pin);
    await tester.tap(find.textContaining('Pagar \$'));
    await tester.pump();
    await tester.pumpAndSettle();
  }

  group('cuentas', () {
    testWidgets('carga, sin cuentas y errores con reintento', (tester) async {
      final espera = Completer<List<Cuenta>>();
      await abrir(tester, recarga, cuentas: () => espera.future);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      espera.complete([]);
      await tester.pumpAndSettle();
      expect(find.text('No tienes una cuenta activa'), findsOneWidget);

      var intento = 0;
      await abrir(
        tester,
        recarga,
        cuentas: () async {
          intento++;
          if (intento == 1) throw const ApiException(500, 'Cuentas caídas');
          if (intento == 2) throw StateError('raro');
          return [_cuenta1];
        },
      );
      await tester.pumpAndSettle();
      expect(find.text('Cuentas caídas'), findsOneWidget);
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('No pudimos cargar tu cuenta.'), findsOneWidget);
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);
    });

    testWidgets('cerrar el panel antes de que lleguen las cuentas no falla', (tester) async {
      final espera = Completer<List<Cuenta>>();
      await abrir(tester, recarga, cuentas: () => espera.future);
      await tester.pumpWidget(const SizedBox());
      espera.complete([_cuenta1]);
      await tester.pumpAndSettle();

      final espera2 = Completer<List<Cuenta>>();
      await abrir(tester, recarga, cuentas: () => espera2.future);
      await tester.pumpWidget(const SizedBox());
      espera2.completeError(const ApiException(500, 'x'));
      await tester.pumpAndSettle();

      final espera3 = Completer<List<Cuenta>>();
      await abrir(tester, recarga, cuentas: () => espera3.future);
      await tester.pumpWidget(const SizedBox());
      espera3.completeError(StateError('x'));
      await tester.pumpAndSettle();
    });
  });

  group('formulario de recarga', () {
    testWidgets('valida, muestra el total y una sola cuenta fija', (tester) async {
      await abrir(tester, recarga);
      await tester.pumpAndSettle();
      expect(find.textContaining('Pagas desde'), findsOneWidget);
      expect(find.textContaining('50.00'), findsWidgets);

      await tester.tap(find.text('Realizar compra'));
      await tester.pump();
      expect(find.text('Escribe el número de celular.'), findsOneWidget);
    });

    testWidgets('varias cuentas: elegir otra y saldo insuficiente', (tester) async {
      await abrir(tester, recarga, cuentas: () async => [_cuenta1, _cuenta2]);
      await tester.pumpAndSettle();
      await tester.tap(find.byType(DropdownButtonFormField<Cuenta>));
      await tester.pumpAndSettle();
      await tester.tap(find.textContaining('6655').last);
      await tester.pumpAndSettle();

      await ponerCelular(tester);
      await revisar(tester);
      expect(find.text('Saldo insuficiente'), findsOneWidget);
      expect(find.textContaining('Tu saldo es'), findsOneWidget);

      // Cambiar de cuenta limpia el aviso.
      await tester.tap(find.byType(DropdownButtonFormField<Cuenta>));
      await tester.pumpAndSettle();
      await tester.tap(find.textContaining('5678').last);
      await tester.pumpAndSettle();
      expect(find.text('Saldo insuficiente'), findsNothing);
    });
  });

  group('diálogo de confirmación', () {
    testWidgets('cancelar vuelve al formulario sin cobrar', (tester) async {
      final e = await abrir(tester, recarga);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      expect(find.text('Confirma tu compra'), findsOneWidget);
      expect(find.text('55 1234 5678'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);
      expect(e.confirmadas, 0);
    });

    testWidgets('PIN inválido, mostrar/ocultar y confirmar con el teclado', (tester) async {
      await abrir(tester, recarga);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);

      await tester.tap(find.textContaining('Pagar \$'));
      await tester.pump();
      expect(find.text('Escribe tu PIN de autorización.'), findsOneWidget);

      await tester.tap(find.byTooltip('Mostrar PIN'));
      await tester.pump();
      expect(find.byTooltip('Ocultar PIN'), findsOneWidget);
      await tester.tap(find.byTooltip('Ocultar PIN'));
      await tester.pump();

      await tester.enterText(campo('PIN de autorización'), '1234');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(find.text('Recarga 50'), findsWidgets); // recibo del pago aprobado
    });
  });

  group('resultado de la compra', () {
    testWidgets('aprobada: recibo, historial y comprar otro', (tester) async {
      final e = await abrir(tester, recarga);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('Compra confirmada'), findsOneWidget);
      expect(e.confirmadas, 1);
      expect(e.cuentas.llamadas, 2); // el saldo se recargó

      await tester.tap(find.text('Ver mis compras'));
      expect(e.verHistorial, 1);
      await tester.tap(find.text('Comprar otro'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);
    });

    testWidgets('mientras se procesa muestra el aviso de espera', (tester) async {
      final espera = Completer<Comprobante>();
      final pagos = PagosFalso()..alPagar = (_) => espera.future;
      await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await tester.enterText(campo('PIN de autorización'), '1234');
      await tester.tap(find.textContaining('Pagar \$'));
      await tester.pump();
      await tester.pump();
      expect(find.textContaining('Procesando tu pago'), findsOneWidget);
      espera.complete(_aprobado());
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
    });

    testWidgets('rechazada: corregir y reintentar conserva los datos', (tester) async {
      final pagos = PagosFalso()
        ..alPagar = (_) async => throw const ApiException(422, 'PIN incorrecto');
      await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('Compra rechazada'), findsOneWidget);
      expect(find.textContaining('PIN incorrecto'), findsOneWidget);
      await tester.tap(find.text('Corregir y reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('5512345678'), findsOneWidget);
    });

    testWidgets('cuenta con el PIN bloqueado: no deja comprar y avisa', (tester) async {
      final bloqueada = Cuenta(
        id: 1,
        numeroCuenta: '123456789012345678',
        saldo: 1000,
        activa: true,
        pinBloqueadoHasta: DateTime.now().add(const Duration(minutes: 15)),
      );
      await abrir(tester, recarga, cuentas: () async => [bloqueada]);
      await tester.pumpAndSettle();
      expect(find.text('PIN bloqueado temporalmente'), findsOneWidget);
      expect(find.text('Compras bloqueadas'), findsOneWidget);
      expect(find.text('Realizar compra'), findsNothing);
      expect(find.textContaining('Podrás volver a comprar a las'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1)); // la cuenta regresiva avanza
      expect(find.text('Compras bloqueadas'), findsOneWidget);
    });

    testWidgets('el bloqueo termina solo y vuelve a permitir comprar', (tester) async {
      final casiLibre = Cuenta(
        id: 1,
        numeroCuenta: '123456789012345678',
        saldo: 1000,
        activa: true,
        pinBloqueadoHasta: DateTime.now().add(const Duration(milliseconds: 300)),
      );
      await abrir(tester, recarga, cuentas: () async => [casiLibre]);
      await tester.pumpAndSettle();
      expect(find.text('Compras bloqueadas'), findsOneWidget);
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 400)));
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Realizar compra'), findsOneWidget);
      expect(find.text('Compras bloqueadas'), findsNothing);
    });

    testWidgets('el PIN que se bloquea al pagar muestra el bloqueo y no deja reintentar', (tester) async {
      final pagos = PagosFalso()
        ..alPagar = (_) async => throw const ApiException(
          422,
          'PIN incorrecto; se bloqueó por 15 minutos por intentos fallidos',
        );
      var consultas = 0;
      await abrir(
        tester,
        recarga,
        pagos: pagos,
        cuentas: () async {
          consultas++;
          if (consultas == 1) return [_cuenta1];
          return [
            Cuenta(
              id: 1,
              numeroCuenta: _cuenta1.numeroCuenta,
              saldo: 1000,
              activa: true,
              pinBloqueadoHasta: DateTime.now().add(const Duration(minutes: 15)),
            ),
          ];
        },
      );
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('PIN bloqueado temporalmente'), findsOneWidget);
      expect(find.text('Corregir y reintentar'), findsNothing);
      await tester.tap(find.text('Entendido'));
      await tester.pumpAndSettle();
      expect(find.text('Compras bloqueadas'), findsOneWidget);
      expect(find.text('Realizar compra'), findsNothing);
    });

    testWidgets('si el bloqueo del resultado vence, se puede corregir y reintentar', (tester) async {
      final pagos = PagosFalso()
        ..alPagar = (_) async => throw const ApiException(
          422,
          'PIN incorrecto; se bloqueó por 15 minutos por intentos fallidos',
        );
      var consultas = 0;
      await abrir(
        tester,
        recarga,
        pagos: pagos,
        cuentas: () async {
          consultas++;
          if (consultas == 1) return [_cuenta1];
          return [
            Cuenta(
              id: 1,
              numeroCuenta: _cuenta1.numeroCuenta,
              saldo: 1000,
              activa: true,
              pinBloqueadoHasta: DateTime.now().add(const Duration(milliseconds: 300)),
            ),
          ];
        },
      );
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('PIN bloqueado temporalmente'), findsOneWidget);

      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 400)));
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      expect(find.text('Corregir y reintentar'), findsOneWidget);
    });

    testWidgets('error de comunicación incierto y no incierto', (tester) async {
      final pagos = PagosFalso()..alPagar = (_) async => throw const ConexionException();
      final e = await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('No pudimos confirmar el resultado'), findsOneWidget);
      await tester.tap(find.text('Revisar mis compras'));
      expect(e.verHistorial, 1);

      final pagos2 = PagosFalso()
        ..alPagar = (_) async => throw const ApiException(502, 'No llegó al proveedor');
      await abrir(tester, recarga, pagos: pagos2);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('No se pudo completar la compra'), findsOneWidget);
      await tester.tap(find.text('Intentar de nuevo'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);
    });

    testWidgets('si se cierra el panel durante el pago no falla', (tester) async {
      final espera = Completer<Comprobante>();
      final pagos = PagosFalso()..alPagar = (_) => espera.future;
      await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await tester.enterText(campo('PIN de autorización'), '1234');
      await tester.tap(find.textContaining('Pagar \$'));
      await tester.pump();
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      espera.complete(_aprobado());
      await tester.pumpAndSettle();
    });
  });

  group('compra pendiente', () {
    PagosFalso pendiente(Future<EstadoPago> Function(String)? consulta) =>
        PagosFalso()
          ..alConsultar = consulta
          ..alPagar = (_) async => throw const ApiException(504, 'En revisión: ABCDEFGHIJ12345');

    Future<_Entorno> hastaPendiente(WidgetTester tester, PagosFalso pagos) async {
      final e = await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('Compra pendiente'), findsOneWidget);
      expect(find.text('ABCDEFGHIJ12345'), findsOneWidget);
      return e;
    }

    EstadoPago estado(String s) => EstadoPago(upc: 'ABCDEFGHIJ12345', estado: s, mensaje: 'm');

    testWidgets('consulta aprobada', (tester) async {
      final e = await hastaPendiente(tester, pendiente((_) async => estado('APROBADO')));
      await tester.tap(find.text('Consultar estado'));
      await tester.pumpAndSettle();
      expect(find.text('Compra confirmada'), findsOneWidget);
      expect(e.confirmadas, 1);
      await tester.tap(find.text('Ver mis compras'));
      expect(e.verHistorial, 1);
    });

    testWidgets('consulta rechazada', (tester) async {
      await hastaPendiente(tester, pendiente((_) async => estado('RECHAZADO')));
      await tester.tap(find.text('Consultar estado'));
      await tester.pumpAndSettle();
      expect(find.textContaining('saldo fue devuelto'), findsOneWidget);
      await tester.tap(find.text('Volver al catálogo'));
      await tester.pumpAndSettle();
      expect(find.text('Realizar compra'), findsOneWidget);
    });

    testWidgets('consulta en proceso, con espera y luego error', (tester) async {
      final espera = Completer<EstadoPago>();
      var llamadas = 0;
      await hastaPendiente(tester, pendiente((_) {
        llamadas++;
        if (llamadas == 1) return espera.future;
        if (llamadas == 2) throw const ApiException(500, 'Consulta caída');
        throw StateError('raro');
      }));
      await tester.tap(find.text('Consultar estado'));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      espera.complete(estado('PENDIENTE'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Sigue en proceso'), findsOneWidget);

      await tester.tap(find.text('Consultar estado'));
      await tester.pumpAndSettle();
      expect(find.text('Consulta caída'), findsOneWidget);
      await tester.tap(find.text('Consultar estado'));
      await tester.pumpAndSettle();
      expect(find.text('No pudimos consultar el estado.'), findsOneWidget);
    });

    testWidgets('ir a mis compras desde la pendiente', (tester) async {
      final e = await hastaPendiente(tester, pendiente((_) async => estado('PENDIENTE')));
      await tester.tap(find.text('Ir a mis compras'));
      expect(e.verHistorial, 1);
    });

    testWidgets('pendiente sin referencia no ofrece consultar', (tester) async {
      final pagos = PagosFalso()
        ..alPagar = (_) async => Comprobante(
          estado: 'PENDIENTE',
          monto: 50,
          comision: 0,
          registradoEn: DateTime(2026),
        );
      await abrir(tester, recarga, pagos: pagos);
      await tester.pumpAndSettle();
      await ponerCelular(tester);
      await revisar(tester);
      await pagarConPin(tester);
      expect(find.text('Compra pendiente'), findsOneWidget);
      expect(find.text('Consultar estado'), findsNothing);
    });

    testWidgets('cerrar el panel durante la consulta no falla', (tester) async {
      final espera = Completer<EstadoPago>();
      await hastaPendiente(tester, pendiente((_) => espera.future));
      await tester.tap(find.text('Consultar estado'));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      espera.complete(estado('APROBADO'));
      await tester.pumpAndSettle();

      final espera2 = Completer<EstadoPago>();
      await hastaPendiente(tester, pendiente((_) => espera2.future));
      await tester.tap(find.text('Consultar estado'));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      espera2.completeError(const ApiException(500, 'x'));
      await tester.pumpAndSettle();

      final espera3 = Completer<EstadoPago>();
      await hastaPendiente(tester, pendiente((_) => espera3.future));
      await tester.tap(find.text('Consultar estado'));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      espera3.completeError(StateError('x'));
      await tester.pumpAndSettle();
    });
  });

  group('pago de servicio', () {
    Future<void> llenar(WidgetTester tester, {String monto = '100'}) async {
      await tester.enterText(campo('Referencia'), 'REF123');
      await tester.enterText(campo('Monto a pagar'), monto);
      await tester.pump();
    }

    testWidgets('total con comisión, saldo insuficiente y pago', (tester) async {
      final pagos = PagosFalso()..alPagar = (_) async => _aprobado(monto: 100);
      await abrir(tester, servicio, pagos: pagos);
      await tester.pumpAndSettle();
      expect(find.text('Número de servicio'), findsOneWidget); // texto de ayuda
      expect(find.text('—'), findsOneWidget);

      await llenar(tester, monto: '5000');
      await revisar(tester);
      expect(find.text('Saldo insuficiente'), findsOneWidget);
      // Escribir un monto nuevo limpia el aviso.
      await tester.enterText(campo('Monto a pagar'), '100');
      await tester.pump();
      expect(find.text('Saldo insuficiente'), findsNothing);
      expect(find.textContaining('Incluye'), findsOneWidget);

      await revisar(tester);
      expect(
        find.descendant(of: find.byType(AlertDialog), matching: find.text('REF123')),
        findsOneWidget,
      );
      expect(find.text('Comisión'), findsOneWidget);
      await pagarConPin(tester);
      expect(find.text('Compra confirmada'), findsOneWidget);

      await tester.tap(find.text('Comprar otro'));
      await tester.pumpAndSettle();
      expect(find.text('REF123'), findsNothing); // se limpió el formulario
    });

    testWidgets('sin tipo de referencia usa el texto por omisión', (tester) async {
      await abrir(tester, producto('Agua', 'SACMEX', 8, tipo: 2, precio: 0));
      await tester.pumpAndSettle();
      expect(find.text('La encuentras en tu recibo'), findsOneWidget);
      expect(find.textContaining('Incluye'), findsNothing);
    });
  });

  group('código canjeable', () {
    testWidgets('no pide celular ni referencia', (tester) async {
      final canje = producto('Gift 100', 'Amazon', 9, precio: 100, cat: 10);
      await abrir(tester, canje);
      await tester.pumpAndSettle();
      expect(find.text('Número de celular'), findsNothing);
      await revisar(tester);
      expect(find.text('Confirma tu compra'), findsOneWidget);
      expect(find.text('Celular'), findsNothing);
    });
  });
}
