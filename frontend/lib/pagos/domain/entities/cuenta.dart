/// Cuenta del cliente desde la que se paga. El saldo ya incluye las retenciones
/// de pagos en proceso.
class Cuenta {
  const Cuenta({
    required this.id,
    required this.numeroCuenta,
    required this.saldo,
    required this.activa,
  });

  final int id;
  final String numeroCuenta;
  final double saldo;
  final bool activa;
}
