import '../../../core/config/app_config.dart';
import '../../../core/error/app_exception.dart';
import '../../../core/network/api_client.dart';
import '../../domain/entities/comprobante.dart';
import '../../domain/entities/compra_historial.dart';
import '../../domain/entities/estado_pago.dart';
import '../../domain/entities/solicitud_compra.dart';
import '../../domain/ports/pagos_repository.dart';

/// Adaptador HTTP del puerto [PagosRepository].
///
/// POST /pagos  (Bearer)
///   {"numeroCuenta":"18 dígitos","idProducto":int,"idServicio":int,
///    "monto":number(2 dec),"telefono":"10 dígitos"?,"referencia":"alfanum"?,
///    "pinAutorizacion":"4-6 dígitos"}
///   200 -> datos: {estado,idTx,numAutorizacion,monto,comision,referencia,
///                  saldoDisponible,fecha,pin?,legend?}
///   400 validación · 403 PIN/propiedad · 404 producto · 409 duplicado/cuenta
///   422 saldo o rechazo · 423 PIN bloqueado · 429 límite · 502 sin cargo
///   503 sin cargo · 504 en revisión (saldo retenido)
///
/// POST /pagos/consulta (Bearer)  {"upc":"15 caracteres A-Z0-9"}
///   200 -> datos: {upc,estado,mensaje,monto,comision,referencia,idTx?,numAutorizacion?}
///   404 no existe o es de otro usuario
///
/// GET /pagos?pagina=0&tamanio=20 (Bearer)  -> pagos del usuario en cualquier estado
///   200 -> datos: {datos:[...],pagina,tamanoPagina,totalElementos,totalPaginas}
class ApiPagosRepository implements PagosRepository {
  ApiPagosRepository(this._api);
  final ApiClient _api;

  @override
  Future<Comprobante> pagar(SolicitudCompra solicitud) async {
    final respuesta = await _api.post(
      '/pagos',
      timeout: AppConfig.tiempoEsperaPago,
      body: {
        'numeroCuenta': solicitud.numeroCuenta,
        'idProducto': solicitud.producto.idProducto,
        'idServicio': solicitud.producto.idServicio,
        'monto': solicitud.monto,
        if (solicitud.telefono != null) 'telefono': solicitud.telefono,
        if (solicitud.referencia != null) 'referencia': solicitud.referencia,
        'pinAutorizacion': solicitud.pin,
      },
    );
    final d = respuesta.dataAsMap;
    final estado = d['estado'];
    if (estado is! String) {
      throw const ConexionException('La respuesta del servidor no es válida.');
    }
    return Comprobante(
      estado: estado,
      monto: _numero(d['monto']) ?? solicitud.monto,
      comision: _numero(d['comision']) ?? 0,
      registradoEn: DateTime.now(),
      idTx: (d['idTx'] as num?)?.toInt(),
      numAutorizacion: d['numAutorizacion'] as String?,
      referencia: d['referencia'] as String?,
      saldoDisponible: _numero(d['saldoDisponible']),
      fechaProveedor: d['fecha'] as String?,
      codigoCanje: d['pin'] as String?,
      leyenda: d['legend'] as String?,
    );
  }

  @override
  Future<EstadoPago> consultarEstado(String upc) async {
    final respuesta = await _api.post(
      '/pagos/consulta',
      body: {'upc': upc},
      timeout: AppConfig.tiempoEsperaConsulta,
    );
    final d = respuesta.dataAsMap;
    final estado = d['estado'];
    if (estado is! String) {
      throw const ConexionException('La respuesta del servidor no es válida.');
    }
    return EstadoPago(
      upc: (d['upc'] as String?) ?? upc,
      estado: estado,
      mensaje: (d['mensaje'] as String?) ?? '',
      monto: _numero(d['monto']),
      comision: _numero(d['comision']),
      referencia: d['referencia'] as String?,
      idTx: (d['idTx'] as num?)?.toInt(),
      numAutorizacion: d['numAutorizacion'] as String?,
    );
  }

  @override
  Future<Pagina<CompraHistorial>> historial({
    required int pagina,
    required int tamanio,
  }) async {
    final respuesta = await _api.get(
      '/pagos',
      query: {'pagina': '$pagina', 'tamanio': '$tamanio'},
    );
    final d = respuesta.dataAsMap;
    final lista = d['datos'];
    final compras = (lista is List ? lista : const [])
        .whereType<Map<String, dynamic>>()
        .map(_aCompra)
        .whereType<CompraHistorial>()
        .toList(growable: false);
    return Pagina(
      elementos: compras,
      pagina: (d['pagina'] as num?)?.toInt() ?? pagina,
      totalPaginas: (d['totalPaginas'] as num?)?.toInt() ?? 1,
      totalElementos: (d['totalElementos'] as num?)?.toInt() ?? compras.length,
    );
  }

  CompraHistorial? _aCompra(Map<String, dynamic> j) {
    final upc = j['upc'];
    final fecha = DateTime.tryParse('${j['fecha']}');
    final monto = _numero(j['monto']);
    if (upc is! String || fecha == null || monto == null) return null;
    final comision = _numero(j['comision']) ?? 0;
    return CompraHistorial(
      upc: upc,
      producto: j['producto'] as String?,
      servicio: j['servicio'] as String?,
      monto: monto,
      comision: comision,
      total: _numero(j['total']) ?? monto + comision,
      fecha: fecha,
      referencia: j['referencia'] as String?,
      telefono: j['telefono'] as String?,
      idTx: (j['idTx'] as num?)?.toInt(),
      numAutorizacion: j['numAutorizacion'] as String?,
      estado: (j['estado'] as String?) ?? 'APROBADO',
    );
  }

  double? _numero(Object? valor) => valor is num ? valor.toDouble() : null;
}
