import '../entities/desafio_verificacion.dart';
import '../entities/evento_sesion.dart';
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

  /// Eventos del servidor sobre esta sesión por un único canal: `conectado` al abrirlo,
  /// `sesion_cerrada` (con el motivo) en cuanto se cierra o la reemplaza otro dispositivo
  /// y `notificacion` por cada aviso nuevo. Termina o falla si se corta la conexión;
  /// falla con `ApiException` 401 si la sesión ya no era válida al conectar.
  Stream<EventoSesion> eventos();
}
