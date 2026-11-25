import '../entities/notificacion.dart';

/// Puerto de salida hacia la bandeja de notificaciones del servidor.
abstract interface class NotificacionesRepository {
  Future<PaginaNotificaciones> listar({
    required int pagina,
    required int tamanio,
  });

  Future<void> marcarLeida(int id);

  Future<void> marcarTodasLeidas();
}
