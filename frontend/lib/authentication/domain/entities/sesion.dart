/// Sesión autenticada. El token es el JWT que emite la API; nunca se muestra.
class Sesion {
  const Sesion({
    required this.token,
    required this.correo,
    required this.nombreCompleto,
    required this.expiraEn,
  });

  final String token;
  final String correo;
  final String nombreCompleto;
  final DateTime expiraEn;

  bool get estaVigente => DateTime.now().isBefore(expiraEn);

  Duration get tiempoRestante => expiraEn.difference(DateTime.now());

  /// Nombre corto para saludos: primer nombre, o la parte local del correo.
  String get nombreCorto {
    final nombre = nombreCompleto.trim();
    if (nombre.isEmpty) return correo.split('@').first;
    return nombre.split(RegExp(r'\s+')).first;
  }
}
