/// Primer paso del acceso completado: la API envió un código de un solo uso al
/// correo del usuario. El [desafioId] enlaza ese código con el segundo paso.
class DesafioVerificacion {
  const DesafioVerificacion({
    required this.desafioId,
    required this.correo,
    required this.correoEnmascarado,
    required this.vigencia,
  });

  final String desafioId;

  /// Correo con el que se inició el acceso (para mostrar y para la sesión).
  final String correo;

  /// Ej.: `a***@correo.com`; la API nunca devuelve el correo completo.
  final String correoEnmascarado;
  final Duration vigencia;
}
