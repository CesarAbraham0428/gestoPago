import '../entities/sesion.dart';

/// Puerto de salida: dónde se conserva la sesión entre arranques de la app.
abstract interface class AlmacenSesion {
  Future<Sesion?> leer();
  Future<void> guardar(Sesion sesion);
  Future<void> borrar();
}
