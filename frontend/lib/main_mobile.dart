import 'app/app.dart';
import 'app/mobile/mobile_shell.dart';

/// Aplicación móvil (Android / iOS).
///   flutter run -t lib/main_mobile.dart
/// En el emulador de Android la API local es http://10.0.2.2:8080:
///   flutter run -t lib/main_mobile.dart --dart-define=API_BASE_URL=http://10.0.2.2:8080
Future<void> main() => iniciarAplicacion(construirShellMovil);
