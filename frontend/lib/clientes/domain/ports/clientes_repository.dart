import '../entities/cliente_admin.dart';

/// Puerto de salida: administración de clientes (solo para administradores).
abstract interface class ClientesRepository {
  /// Si el usuario autenticado tiene el rol de administrador.
  Future<bool> esAdministrador();

  /// Todos los clientes de la aplicación excepto el usuario autenticado.
  Future<List<ClienteAdmin>> listar();

  /// Activa o desactiva la cuenta del cliente con [rfc].
  Future<void> cambiarEstado({required String rfc, required bool activo});
}
