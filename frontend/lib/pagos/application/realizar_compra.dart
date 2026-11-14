import '../../core/error/app_exception.dart';
import '../domain/entities/resultado_compra.dart';
import '../domain/entities/solicitud_compra.dart';
import '../domain/ports/pagos_repository.dart';

/// Caso de uso: enviar la compra y clasificar el desenlace.
///
/// Regla central: solo un comprobante en estado APROBADO es una compra
/// realizada. Un error de red después de enviar el pago deja el resultado
/// incierto: no se presenta como fallo seguro ni se invita a reintentar.
class RealizarCompra {
  RealizarCompra(this._pagos);
  final PagosRepository _pagos;

  static final _formatoReferencia = RegExp(r'\b[A-Z0-9]{15}\b');

  Future<ResultadoCompra> ejecutar(SolicitudCompra solicitud) async {
    try {
      final comprobante = await _pagos.pagar(solicitud);
      if (comprobante.estaAprobado) return CompraExitosa(comprobante);
      return const CompraPendiente(
        'El servicio aún no confirma el pago. Tu saldo está retenido y se '
        'resolverá solo; no lo intentes de nuevo.',
      );
    } on ApiException catch (e) {
      return _desdeError(e);
    } on ConexionException {
      return const ErrorDeComunicacion(
        'No recibimos respuesta del servidor. Es posible que el pago sí se '
        'haya realizado: revisa tu historial antes de intentarlo de nuevo.',
        resultadoIncierto: true,
      );
    } on Object {
      return const ErrorDeComunicacion(
        'Ocurrió un problema inesperado. Revisa tu historial antes de '
        'intentarlo de nuevo.',
        resultadoIncierto: true,
      );
    }
  }

  ResultadoCompra _desdeError(ApiException e) {
    switch (e.statusCode) {
      case 504:
        // El pago quedó EN_REVISION; el mensaje trae la referencia (UPC).
        return CompraPendiente(
          e.message,
          referencia: _formatoReferencia.firstMatch(e.message)?.group(0),
        );
      case 502:
        // El proveedor nunca recibió el pago: la API devolvió el saldo.
        return ErrorDeComunicacion(e.message, resultadoIncierto: false);
      case 401:
        return const CompraRechazada('Tu sesión terminó. Inicia sesión de nuevo.');
      case >= 500:
        return const ErrorDeComunicacion(
          'El servicio tuvo un problema. Revisa tu historial antes de '
          'intentarlo de nuevo.',
          resultadoIncierto: true,
        );
      default:
        // 400, 403, 404, 409, 422, 423, 429, 503: la API rechazó antes de cobrar.
        return CompraRechazada(e.message);
    }
  }
}
