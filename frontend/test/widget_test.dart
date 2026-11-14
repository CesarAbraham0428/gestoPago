import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/format/formatters.dart';
import 'package:frontend/pagos/application/realizar_compra.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/domain/entities/resultado_compra.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/domain/ports/pagos_repository.dart';
import 'package:frontend/productos/domain/entities/producto.dart';

class _PagosFalso implements PagosRepository {
  _PagosFalso(this._respuesta);
  final Future<Comprobante> Function() _respuesta;

  @override
  Future<Comprobante> pagar(SolicitudCompra solicitud) => _respuesta();

  @override
  Future<EstadoPago> consultarEstado(String upc) => throw UnimplementedError();

  @override
  Future<Pagina<CompraHistorial>> historial({required int pagina, required int tamanio}) =>
      throw UnimplementedError();
}

const _recarga = Producto(
  nombre: 'Recarga \$50',
  servicio: 'Telcel',
  idProducto: 1,
  idServicio: 2,
  tipoFront: 1,
  precio: 50,
);

final _solicitud = SolicitudCompra(
  producto: _recarga,
  numeroCuenta: '123456789012345678',
  monto: 50,
  pin: '1234',
  telefono: '5512345678',
);

Comprobante _comprobante(String estado) => Comprobante(
  estado: estado,
  monto: 50,
  comision: 0,
  registradoEn: DateTime(2026, 1, 1),
);

Future<ResultadoCompra> _ejecutar(Future<Comprobante> Function() respuesta) =>
    RealizarCompra(_PagosFalso(respuesta)).ejecutar(_solicitud);

void main() {
  group('RealizarCompra distingue los cuatro desenlaces', () {
    test('APROBADO es compra exitosa', () async {
      final r = await _ejecutar(() async => _comprobante('APROBADO'));
      expect(r, isA<CompraExitosa>());
    });

    test('200 con un estado distinto de APROBADO no es compra realizada', () async {
      final r = await _ejecutar(() async => _comprobante('EN_REVISION'));
      expect(r, isA<CompraPendiente>());
    });

    test('422 es rechazada y conserva el mensaje del servidor', () async {
      final r = await _ejecutar(
        () async => throw const ApiException(422, 'Saldo insuficiente para realizar el pago'),
      );
      expect(r, isA<CompraRechazada>());
      expect((r as CompraRechazada).mensaje, contains('Saldo insuficiente'));
    });

    test('504 es pendiente y extrae la referencia', () async {
      final r = await _ejecutar(
        () async => throw const ApiException(
          504,
          'No se recibió confirmación del proveedor. El pago (referencia '
          'ABCDEFGHIJ12345) quedó en revisión.',
        ),
      );
      expect(r, isA<CompraPendiente>());
      expect((r as CompraPendiente).referencia, 'ABCDEFGHIJ12345');
    });

    test('502 es error de comunicación con resultado conocido', () async {
      final r = await _ejecutar(() async => throw const ApiException(502, 'sin cargo'));
      expect(r, isA<ErrorDeComunicacion>());
      expect((r as ErrorDeComunicacion).resultadoIncierto, isFalse);
    });

    test('sin respuesta del servidor deja el resultado incierto', () async {
      final r = await _ejecutar(() async => throw const ConexionException());
      expect(r, isA<ErrorDeComunicacion>());
      expect((r as ErrorDeComunicacion).resultadoIncierto, isTrue);
    });
  });

  group('Producto', () {
    test('una recarga sin precio no se puede comprar', () {
      const p = Producto(
        nombre: 'X', servicio: 'Y', idProducto: 1, idServicio: 1, tipoFront: 1,
      );
      expect(p.tipoCompra, TipoCompra.noDisponible);
    });

    test('tipoFront 2 es pago de servicio y el precio es comisión', () {
      const p = Producto(
        nombre: 'CFE', servicio: 'CFE', idProducto: 1, idServicio: 1,
        tipoFront: 2, precio: 12,
      );
      expect(p.tipoCompra, TipoCompra.pagoServicio);
      expect(p.comision, 12);
      expect(p.montoFijo, isNull);
    });

    test('tipoFront 4 (consulta) no está disponible', () {
      const p = Producto(
        nombre: 'Consulta', servicio: 'CFE', idProducto: 1, idServicio: 1, tipoFront: 4,
      );
      expect(p.disponibleParaCompra, isFalse);
    });
  });

  group('parseMonto', () {
    test('acepta hasta dos decimales', () {
      expect(parseMonto('125.5'), 125.5);
      expect(parseMonto('125,50'), 125.5);
    });

    test('rechaza tres decimales, negativos y texto', () {
      expect(parseMonto('1.234'), isNull);
      expect(parseMonto('-5'), isNull);
      expect(parseMonto('abc'), isNull);
      expect(parseMonto(''), isNull);
    });
  });
}
