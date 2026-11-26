import '../../../perfil/domain/entities/perfil.dart';

/// Un cliente visto por un administrador: los mismos datos del perfil más el
/// estado de su cuenta de usuario.
class ClienteAdmin {
  const ClienteAdmin({
    required this.id,
    required this.perfil,
    required this.activo,
    this.esAdministrador = false,
    this.fechaAlta,
  });

  final int id;
  final Perfil perfil;
  final bool activo;
  final bool esAdministrador;
  final DateTime? fechaAlta;

  String get nombre => perfil.nombreCompleto;

  String get correo => perfil.correoElectronico;

  /// Primera letra del nombre y del primer apellido: "Ana Pérez" -> "AP".
  String get iniciales {
    final n = perfil.primerNombre.trim();
    final a = perfil.apellidoPaterno.trim();
    final texto = '${n.isEmpty ? '' : n[0]}${a.isEmpty ? '' : a[0]}';
    return texto.isEmpty ? '?' : texto.toUpperCase();
  }
}
