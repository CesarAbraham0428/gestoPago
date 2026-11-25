import '../../../productos/domain/entities/producto.dart';

/// Lo que el usuario confirma antes de pagar. El código OTP de la app
/// autenticadora solo vive aquí hasta enviarse; no se guarda ni se muestra.
class SolicitudCompra {
  const SolicitudCompra({
    required this.producto,
    required this.numeroCuenta,
    required this.monto,
    required this.codigoOtp,
    this.telefono,
    this.referencia,
  });

  final Producto producto;
  final String numeroCuenta;
  final double monto;
  final String codigoOtp;
  final String? telefono;
  final String? referencia;

  /// Cargo total: monto más comisión del servicio.
  double get total => monto + producto.comision;

  @override
  String toString() => 'SolicitudCompra(${producto.clave}, monto: $monto)'; // sin código ni cuenta
}
