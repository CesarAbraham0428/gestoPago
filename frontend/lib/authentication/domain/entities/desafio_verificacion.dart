/// Datos para enlazar la app de autenticación (Google Authenticator, Microsoft
/// Authenticator, Authy…). Solo vienen la primera vez, mientras la cuenta no la
/// tiene enlazada.
class EnrolamientoTotp {
  const EnrolamientoTotp({required this.secreto, required this.otpauthUri});

  /// Clave en Base32 para escribirla a mano en la app.
  final String secreto;

  /// URI `otpauth://` que la app lee del código QR.
  final String otpauthUri;
}

/// Primer paso del acceso completado: la contraseña es correcta y falta el
/// código de 6 dígitos que muestra la app de autenticación. El [desafioId]
/// enlaza ambos pasos.
class DesafioVerificacion {
  const DesafioVerificacion({
    required this.desafioId,
    required this.correo,
    required this.vigencia,
    this.enrolamiento,
  });

  final String desafioId;

  /// Correo con el que se inició el acceso (para la sesión).
  final String correo;

  /// Tiempo que tiene el usuario para completar el segundo paso.
  final Duration vigencia;

  /// No nulo la primera vez: hay que enlazar la app antes de escribir el código.
  final EnrolamientoTotp? enrolamiento;
}
