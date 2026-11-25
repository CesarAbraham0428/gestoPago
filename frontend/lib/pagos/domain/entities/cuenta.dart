/// Cuenta del cliente desde la que se paga. El saldo ya incluye las retenciones
/// de pagos en proceso.
class Cuenta {
  const Cuenta({
    required this.id,
    required this.numeroCuenta,
    required this.saldo,
    required this.activa,
    this.pinBloqueadoHasta,
  });

  final int id;
  final String numeroCuenta;
  final double saldo;
  final bool activa;

  /// Tras demasiados códigos incorrectos el servidor bloquea los pagos de la cuenta
  /// hasta este momento; `null` si no está bloqueada.
  final DateTime? pinBloqueadoHasta;

  /// Tiempo que falta para poder pagar de nuevo, o `null` si no hay bloqueo vigente.
  Duration? bloqueoRestante([DateTime? ahora]) {
    final hasta = pinBloqueadoHasta;
    if (hasta == null) return null;
    final resta = hasta.difference(ahora ?? DateTime.now());
    return resta > Duration.zero ? resta : null;
  }
}
