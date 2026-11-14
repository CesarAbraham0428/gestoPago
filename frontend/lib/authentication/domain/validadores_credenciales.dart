/// Validaciones previas al envío. Replican las reglas de `LoginRequest` de la
/// API para evitar viajes inútiles; el servidor sigue siendo la autoridad.
class ValidadoresCredenciales {
  const ValidadoresCredenciales._();

  static final _correo = RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]{2,}$');

  static String? correo(String? valor) {
    final texto = (valor ?? '').trim();
    if (texto.isEmpty) return 'Escribe tu correo electrónico.';
    if (texto.length > 100 || !_correo.hasMatch(texto)) {
      return 'Revisa el correo: debe verse como nombre@dominio.com.';
    }
    return null;
  }

  /// Código de un solo uso del segundo factor: exactamente 6 dígitos.
  static String? codigo(String? valor) {
    final texto = (valor ?? '').trim();
    if (texto.isEmpty) return 'Escribe el código que enviamos a tu correo.';
    if (!RegExp(r'^\d{6}$').hasMatch(texto)) return 'El código tiene 6 dígitos.';
    return null;
  }

  static String? password(String? valor) {
    final texto = valor ?? '';
    if (texto.isEmpty) return 'Escribe tu contraseña.';
    if (texto.length > 72) {
      return 'La contraseña no puede tener más de 72 caracteres.';
    }
    return null;
  }
}
