/// Errores que cruzan las capas. La capa de dominio nunca ve `http`.
sealed class AppException implements Exception {
  const AppException(this.message);
  final String message;

  @override
  String toString() => message;
}

/// El servidor respondió con un estado de error. [message] viene del campo
/// `mensaje` de la API, que ya está redactado para el usuario final.
class ApiException extends AppException {
  const ApiException(this.statusCode, super.message, {this.reintentarEn});
  final int statusCode;

  /// Espera indicada por el servidor (cabecera `Retry-After`) cuando rechazó
  /// la solicitud por exceso de intentos; nula si no la envió.
  final Duration? reintentarEn;

  bool get esSesionInvalida => statusCode == 401;

  /// HTTP 429: demasiadas solicitudes o intentos. El cliente debe esperar.
  bool get esLimiteDeSolicitudes => statusCode == 429;
}

/// El usuario ya tiene una sesión abierta en otro dispositivo y debe confirmar
/// que quiere cerrarla (HTTP 409 al verificar el código de acceso).
class SesionActivaException extends AppException {
  const SesionActivaException(super.message);
}

/// No hubo respuesta utilizable (sin red, tiempo agotado, respuesta ilegible).
/// En una operación de escritura el resultado en el servidor es desconocido.
class ConexionException extends AppException {
  const ConexionException([
    super.message = 'No pudimos comunicarnos con el servidor.',
  ]);
}
