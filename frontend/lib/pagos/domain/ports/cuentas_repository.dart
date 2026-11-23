import '../entities/cuenta.dart';

/// Puerto de salida: cuentas del usuario autenticado.
abstract interface class CuentasRepository {
  Future<List<Cuenta>> obtenerActivas();
}
