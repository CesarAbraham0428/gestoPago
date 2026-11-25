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
}
