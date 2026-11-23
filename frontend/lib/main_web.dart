import 'app/app.dart';
import 'app/web/web_shell.dart';

/// Aplicación web.
///   flutter run -d chrome -t lib/main_web.dart
///   flutter build web -t lib/main_web.dart --dart-define=API_BASE_URL=https://...
Future<void> main() => iniciarAplicacion(construirShellWeb);
