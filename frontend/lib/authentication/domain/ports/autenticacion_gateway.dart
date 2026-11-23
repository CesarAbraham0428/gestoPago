import '../entities/desafio_verificacion.dart';
import '../entities/sesion.dart';

/// Puerto de salida hacia el servicio de autenticación. Todas las validaciones
/// reales (contraseña, código, usuario activo, sesión única) ocurren en el servidor.
abstract interface class AutenticacionGateway {
  /// Paso 1: valida credenciales y hace que el servidor envíe el código.
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  });

  /// Paso 2: valida el código y abre la sesión. Lanza `SesionActivaException`
  /// si el usuario ya tiene otra sesión y [cerrarSesionAnterior] es falso.
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  });

  /// Invalida en el servidor la sesión actual.
  Future<void> cerrarSesion();
}
