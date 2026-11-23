import '../entities/perfil.dart';

/// Puerto de salida: perfil del cliente autenticado.
abstract interface class PerfilRepository {
  Future<Perfil> obtener();

  /// Aplica [cambios] (solo campos modificados) al cliente con [rfc] y
  /// devuelve el perfil actualizado.
  Future<Perfil> actualizar({
    required String rfc,
    required Map<String, Object?> cambios,
  });

  /// Cambia el PIN de autorización de la cuenta. [pinActual] solo hace falta
  /// si la cuenta ya tiene uno.
  Future<void> cambiarPin({
    required String numeroCuenta,
    String? pinActual,
    required String pinNuevo,
  });
}
