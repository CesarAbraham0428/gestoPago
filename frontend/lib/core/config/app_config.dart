/// Configuración de entorno. Se cambia al compilar:
/// `flutter run --dart-define=API_BASE_URL=https://api.ejemplo.com`
///
/// En producción la URL debe ser HTTPS. Una compilación de producción se niega a
/// arrancar con HTTP salvo hacia el propio equipo ([conexionPermitida]), para que
/// las credenciales y el token nunca viajen en claro por la red.
class AppConfig {
  const AppConfig._();

  static const apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  static bool get esConexionSegura => apiBaseUrl.startsWith('https://');

  /// HTTP solo se admite hacia el propio equipo (desarrollo y emulador de Android).
  static bool get conexionPermitida =>
      esConexionSegura ||
      const {'localhost', '127.0.0.1', '10.0.2.2'}.contains(Uri.parse(apiBaseUrl).host);

  /// Sin interacción durante este tiempo se cierra la sesión.
  static const inactividadMaxima = Duration(minutes: 15);

  /// El servidor puede tardar hasta ~70 s en responder un pago (GestoPago).
  static const tiempoEsperaPago = Duration(seconds: 85);
  static const tiempoEsperaConsulta = Duration(seconds: 20);
}
