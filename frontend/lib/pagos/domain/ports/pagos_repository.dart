import '../entities/comprobante.dart';
import '../entities/compra_historial.dart';
import '../entities/estado_pago.dart';
import '../entities/solicitud_compra.dart';

/// Puerto de salida hacia el servicio de pagos.
///
/// [pagar] lanza `ApiException` si el servicio responde con error y
/// `ConexionException` si no hay respuesta; el caso de uso decide qué significa
/// cada una para el usuario.
abstract interface class PagosRepository {
  Future<Comprobante> pagar(SolicitudCompra solicitud);

  Future<EstadoPago> consultarEstado(String upc);

  Future<Pagina<CompraHistorial>> historial({
    required int pagina,
    required int tamanio,
  });
}
