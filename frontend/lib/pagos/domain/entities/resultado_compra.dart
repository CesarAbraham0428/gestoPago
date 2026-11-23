import 'comprobante.dart';

/// Desenlace de una compra. Solo [CompraExitosa] significa que el servicio
/// confirmó el pago; cualquier otro caso nunca debe presentarse como compra
/// realizada.
sealed class ResultadoCompra {
  const ResultadoCompra();
}

/// El servicio confirmó el pago (estado APROBADO).
class CompraExitosa extends ResultadoCompra {
  const CompraExitosa(this.comprobante);
  final Comprobante comprobante;
}

/// El pago no se realizó y no se cobró nada (validación, PIN, saldo, rechazo
/// del proveedor).
class CompraRechazada extends ResultadoCompra {
  const CompraRechazada(this.mensaje);
  final String mensaje;
}

/// El servicio no sabe todavía si el pago se aplicó. El monto queda retenido y
/// se resuelve solo; no se debe repetir el pago. [referencia] permite
/// consultar el estado después.
class CompraPendiente extends ResultadoCompra {
  const CompraPendiente(this.mensaje, {this.referencia});
  final String mensaje;
  final String? referencia;
}

/// Falló la comunicación. Si [resultadoIncierto] es verdadero, la petición pudo
/// haber llegado al servidor y hay que revisar el historial antes de reintentar.
class ErrorDeComunicacion extends ResultadoCompra {
  const ErrorDeComunicacion(this.mensaje, {required this.resultadoIncierto});
  final String mensaje;
  final bool resultadoIncierto;
}
