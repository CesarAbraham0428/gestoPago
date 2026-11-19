/// Textos de error que ve el usuario. El cliente no muestra a ciegas lo que
/// manda el servidor: un mensaje técnico (nombres de campos, formatos, rutas,
/// sistemas internos) o cualquier error 5xx se sustituye por uno genérico, de
/// modo que la pantalla nunca revela detalles de la implementación.
abstract final class MensajesUsuario {
  static const sinConexion =
      'No pudimos conectarnos. Revisa tu conexión a internet e inténtalo de nuevo.';
  static const tiempoAgotado =
      'La conexión está tardando demasiado. Inténtalo de nuevo en un momento.';
  static const respuestaInvalida =
      'Algo salió mal. Inténtalo de nuevo en un momento.';
  static const servicioNoDisponible =
      'El servicio no está disponible en este momento. Inténtalo más tarde.';
  static const sesionTerminada = 'Tu sesión terminó. Inicia sesión de nuevo.';
  static const datosInvalidos = 'Revisa los datos e inténtalo de nuevo.';
  static const sinPermiso = 'No tienes permiso para realizar esta acción.';
  static const noEncontrado = 'No encontramos lo que buscas.';
  static const demasiadosIntentos =
      'Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.';
  static const generico = 'No pudimos completar la operación. Inténtalo de nuevo.';

  // Acceso
  static const credencialesIncorrectas = 'Correo o contraseña incorrectos.';
  static const cuentaNoDisponible =
      'No es posible iniciar sesión con esta cuenta. Comunícate con soporte.';
  static const codigoIncorrecto =
      'El código no es correcto o ya venció. Revísalo e inténtalo de nuevo.';
  static const demasiadosCodigos =
      'Demasiados códigos incorrectos. Vuelve a iniciar sesión para recibir uno nuevo.';

  /// Pistas de que un texto del servidor es técnico y no debe llegar al usuario.
  static final _tecnico = RegExp(
    r'campo|filtro|json|xml|solicitud inválida|ruta|método http|tipo de contenido|'
    r'registro|página inválida|inexistente|no autenticado|gestopago|postgres|redis|'
    r'sql|token|exception|error interno|null|http',
    caseSensitive: false,
  );

  /// Mensaje genérico según el estado HTTP.
  static String porEstado(int estado) => switch (estado) {
    400 || 413 || 415 || 422 => datosInvalidos,
    401 => sesionTerminada,
    403 => sinPermiso,
    404 => noEncontrado,
    429 => demasiadosIntentos,
    >= 500 => servicioNoDisponible,
    _ => generico,
  };

  /// Decide qué mostrar ante un error HTTP: el texto del servidor solo si es
  /// apto para el usuario; si no, el genérico del estado. Los 502 y 504 de los
  /// pagos traen un texto redactado a propósito (si hubo cargo, la referencia),
  /// así que se conservan cuando no parecen técnicos.
  static String desdeServidor(int estado, String? mensaje) {
    final texto = mensaje?.trim() ?? '';
    final pagoConTexto = estado == 502 || estado == 504;
    if (texto.isEmpty || _tecnico.hasMatch(texto) || (estado >= 500 && !pagoConTexto)) {
      return porEstado(estado);
    }
    return _conPunto(texto);
  }

  static String _conPunto(String texto) {
    final limpio = texto.replaceAllMapped(
      RegExp(r';\s+(\S)'),
      (m) => '. ${m[1]!.toUpperCase()}',
    );
    final inicial = limpio[0].toUpperCase() + limpio.substring(1);
    return RegExp(r'[.!?]$').hasMatch(inicial) ? inicial : '$inicial.';
  }
}
