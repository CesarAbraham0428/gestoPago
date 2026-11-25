import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/validadores_credenciales.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/pagos/application/realizar_compra.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/domain/entities/resultado_compra.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/domain/ports/pagos_repository.dart';
import 'package:frontend/pagos/domain/validadores_compra.dart';
import 'package:frontend/productos/domain/entities/producto.dart';

Producto _producto({
  int tipoFront = 1,
  double? precio = 50,
  int? idCat,
}) => Producto(
  nombre: 'Recarga',
  servicio: 'Telcel',
  idProducto: 7,
  idServicio: 9,
  tipoFront: tipoFront,
  idCatTipoServicio: idCat,
  precio: precio,
);

class _Pagos implements PagosRepository {
  _Pagos(this.respuesta);
  final Future<Comprobante> Function() respuesta;

  @override
  Future<Comprobante> pagar(SolicitudCompra solicitud) => respuesta();

  @override
  Future<EstadoPago> consultarEstado(String upc) => throw UnimplementedError();

  @override
  Future<Pagina<CompraHistorial>> historial({
    required int pagina,
    required int tamanio,
  }) => throw UnimplementedError();
}

SolicitudCompra _solicitud() => SolicitudCompra(
  producto: _producto(),
  numeroCuenta: '123456789012345678',
  monto: 50,
  codigoOtp: '123456',
);

void main() {
  group('Sesion', () {
    test('vigencia, tiempo restante y nombre corto', () {
      final vigente = Sesion(
        token: 't',
        correo: 'ana@example.com',
        nombreCompleto: '  Ana María Pérez ',
        expiraEn: DateTime.now().add(const Duration(minutes: 10)),
      );
      expect(vigente.estaVigente, isTrue);
      expect(vigente.tiempoRestante.inMinutes, inInclusiveRange(9, 10));
      expect(vigente.nombreCorto, 'Ana');

      final vencida = Sesion(
        token: 't',
        correo: 'ana@example.com',
        nombreCompleto: '',
        expiraEn: DateTime.now().subtract(const Duration(seconds: 1)),
      );
      expect(vencida.estaVigente, isFalse);
      expect(vencida.nombreCorto, 'ana');
    });
  });

  group('ValidadoresCredenciales', () {
    test('correo', () {
      expect(ValidadoresCredenciales.correo(null), isNotNull);
      expect(ValidadoresCredenciales.correo('  '), isNotNull);
      expect(ValidadoresCredenciales.correo('sin-arroba'), contains('Revisa'));
      expect(ValidadoresCredenciales.correo('${'a' * 100}@b.com'), isNotNull);
      expect(ValidadoresCredenciales.correo('ana@example.com'), isNull);
    });

    test('código', () {
      expect(ValidadoresCredenciales.codigo(null), isNotNull);
      expect(ValidadoresCredenciales.codigo('12345'), contains('6 dígitos'));
      expect(ValidadoresCredenciales.codigo('123456'), isNull);
    });

    test('password', () {
      expect(ValidadoresCredenciales.password(null), isNotNull);
      expect(ValidadoresCredenciales.password(''), isNotNull);
      expect(ValidadoresCredenciales.password('a' * 73), contains('72'));
      expect(ValidadoresCredenciales.password('Segura123!'), isNull);
    });
  });

  group('ValidadoresCompra', () {
    test('teléfono', () {
      expect(ValidadoresCompra.telefono(null), isNotNull);
      expect(ValidadoresCompra.telefono('123'), contains('10 dígitos'));
      expect(ValidadoresCompra.telefono('5512345678'), isNull);
    });

    test('referencia', () {
      expect(ValidadoresCompra.referencia(''), isNotNull);
      expect(ValidadoresCompra.referencia('ab-12'), contains('letras'));
      expect(ValidadoresCompra.referencia('AB12'), isNull);
    });

    test('monto', () {
      expect(ValidadoresCompra.monto(null), isNotNull);
      expect(ValidadoresCompra.monto('1.234'), contains('2 decimales'));
      expect(ValidadoresCompra.monto('0'), contains('mayor a cero'));
      expect(ValidadoresCompra.monto('10.50'), isNull);
    });

  });

  group('Producto', () {
    test('tipo de compra, categoría, monto fijo y comisión', () {
      final recarga = _producto();
      expect(recarga.clave, '7-9');
      expect(recarga.tipoCompra, TipoCompra.recarga);
      expect(recarga.categoria, CategoriaProducto.recargas);
      expect(recarga.categoria.etiqueta, 'Recargas');
      expect(recarga.montoFijo, 50);
      expect(recarga.comision, 0);
      expect(recarga.disponibleParaCompra, isTrue);
      expect(recarga.esCanjeable, isFalse);

      final tarjeta = _producto(idCat: 10);
      expect(tarjeta.esCanjeable, isTrue);
      expect(tarjeta.categoria, CategoriaProducto.tarjetas);
      expect(_producto(idCat: 11).esCanjeable, isTrue);

      final servicio = _producto(tipoFront: 2, precio: 8);
      expect(servicio.tipoCompra, TipoCompra.pagoServicio);
      expect(servicio.categoria, CategoriaProducto.servicios);
      expect(servicio.montoFijo, isNull);
      expect(servicio.comision, 8);
      expect(_producto(tipoFront: 2, precio: null).comision, 0);

      final sinPrecio = _producto(precio: null);
      expect(sinPrecio.tipoCompra, TipoCompra.noDisponible);
      expect(sinPrecio.disponibleParaCompra, isFalse);
      expect(sinPrecio.categoria, CategoriaProducto.otros);
      expect(_producto(precio: 0).tipoCompra, TipoCompra.noDisponible);
      expect(_producto(tipoFront: 3).tipoCompra, TipoCompra.noDisponible);
    });
  });

  group('Entidades de pagos', () {
    test('CompraHistorial y Pagina', () {
      final c = CompraHistorial(
        upc: 'U',
        monto: 1,
        comision: 0,
        total: 1,
        fecha: DateTime(2026),
      );
      expect(c.titulo, 'Compra');
      expect(
        CompraHistorial(
          upc: 'U',
          producto: 'Telcel 50',
          monto: 1,
          comision: 0,
          total: 1,
          fecha: DateTime(2026),
        ).titulo,
        'Telcel 50',
      );
      const p = Pagina<int>(
        elementos: [1],
        pagina: 0,
        totalPaginas: 2,
        totalElementos: 2,
      );
      expect(p.hayMas, isTrue);
      expect(
        const Pagina<int>(
          elementos: [],
          pagina: 1,
          totalPaginas: 2,
          totalElementos: 2,
        ).hayMas,
        isFalse,
      );
    });

    test('Comprobante', () {
      final c = Comprobante(
        estado: 'APROBADO',
        monto: 50,
        comision: 2,
        registradoEn: DateTime(2026),
      );
      expect(c.estaAprobado, isTrue);
      expect(c.total, 52);
      expect(
        Comprobante(
          estado: 'PENDIENTE',
          monto: 1,
          comision: 0,
          registradoEn: DateTime(2026),
        ).estaAprobado,
        isFalse,
      );
    });

    test('EstadoPago', () {
      EstadoPago e(String estado) =>
          EstadoPago(upc: 'U', estado: estado, mensaje: 'm');
      expect(e('APROBADO').aprobado, isTrue);
      expect(e('RECHAZADO').rechazado, isTrue);
      expect(e('PENDIENTE').enProceso, isTrue);
      expect(e('EN_REVISION').enProceso, isTrue);
      expect(e('APROBADO').enProceso, isFalse);
    });

    test('SolicitudCompra: total y toString sin datos sensibles', () {
      final s = SolicitudCompra(
        producto: _producto(tipoFront: 2, precio: 3),
        numeroCuenta: '123456789012345678',
        monto: 100,
        codigoOtp: '123456',
      );
      expect(s.total, 103);
      expect(s.toString(), isNot(contains('9876')));
      expect(s.toString(), isNot(contains('123456789012345678')));
    });

    test('Cuenta y resultados de compra', () {
      const cuenta = Cuenta(id: 1, numeroCuenta: 'X', saldo: 5, activa: true);
      expect(cuenta.saldo, 5);
      expect(cuenta.bloqueoRestante(), isNull);
      final ahora = DateTime(2026, 3, 5, 12);
      final bloqueada = Cuenta(
        id: 1,
        numeroCuenta: 'X',
        saldo: 5,
        activa: true,
        pinBloqueadoHasta: ahora.add(const Duration(minutes: 15)),
      );
      expect(bloqueada.bloqueoRestante(ahora), const Duration(minutes: 15));
      expect(bloqueada.bloqueoRestante(ahora.add(const Duration(minutes: 15))), isNull);
      final comprobante = Comprobante(
        estado: 'APROBADO',
        monto: 1,
        comision: 0,
        registradoEn: DateTime(2026),
      );
      final ResultadoCompra a = CompraExitosa(comprobante);
      final ResultadoCompra b = const CompraRechazada('no');
      final ResultadoCompra c = const CompraPendiente('espera', referencia: 'R');
      final ResultadoCompra d = const ErrorDeComunicacion(
        'red',
        resultadoIncierto: true,
      );
      expect(
        [a, b, c, d].map(
          (r) => switch (r) {
            CompraExitosa() => 'e',
            CompraRechazada() => 'r',
            CompraPendiente() => 'p',
            ErrorDeComunicacion() => 'c',
          },
        ),
        ['e', 'r', 'p', 'c'],
      );
    });
  });

  group('RealizarCompra', () {
    Future<ResultadoCompra> ejecutar(Future<Comprobante> Function() f) =>
        RealizarCompra(_Pagos(f)).ejecutar(_solicitud());

    Comprobante comprobante(String estado) => Comprobante(
      estado: estado,
      monto: 50,
      comision: 0,
      registradoEn: DateTime(2026),
    );

    test('aprobado, pendiente y errores de la API', () async {
      expect(
        await ejecutar(() async => comprobante('APROBADO')),
        isA<CompraExitosa>(),
      );
      expect(
        await ejecutar(() async => comprobante('PENDIENTE')),
        isA<CompraPendiente>(),
      );

      final p504 = await ejecutar(
        () async => throw const ApiException(504, 'Revisa ABCDEFGHIJ12345 luego'),
      );
      expect(p504, isA<CompraPendiente>());
      expect((p504 as CompraPendiente).referencia, 'ABCDEFGHIJ12345');

      final p502 = await ejecutar(
        () async => throw const ApiException(502, 'No llegó'),
      );
      expect((p502 as ErrorDeComunicacion).resultadoIncierto, isFalse);

      final p401 = await ejecutar(
        () async => throw const ApiException(401, 'x'),
      );
      expect((p401 as CompraRechazada).mensaje, contains('sesión terminó'));

      final p500 = await ejecutar(
        () async => throw const ApiException(500, 'x'),
      );
      expect((p500 as ErrorDeComunicacion).resultadoIncierto, isTrue);

      final p422 = await ejecutar(
        () async => throw const ApiException(422, 'Saldo insuficiente'),
      );
      expect((p422 as CompraRechazada).mensaje, 'Saldo insuficiente');
    });

    test('fallos de conexión y errores inesperados son inciertos', () async {
      final red = await ejecutar(() async => throw const ConexionException());
      expect((red as ErrorDeComunicacion).resultadoIncierto, isTrue);
      final raro = await ejecutar(() async => throw StateError('boom'));
      expect((raro as ErrorDeComunicacion).mensaje, contains('inesperado'));
    });
  });
}
