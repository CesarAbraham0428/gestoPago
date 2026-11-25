import '../../../core/network/api_client.dart';
import '../../domain/entities/notificacion.dart';
import '../../domain/ports/notificaciones_repository.dart';

/// Adaptador HTTP del puerto [NotificacionesRepository].
///
/// POST /notificaciones/consultar (Bearer)  {"pagina":0,"tamanio":20}
///   200 -> datos: {lista:{datos:[{id,tipo,titulo,mensaje,upc?,leida,fecha}],pagina,
///                         tamanoPagina,totalElementos,totalPaginas}, noLeidas}
///   400 página fuera de rango (pagina >= 0, tamanio 1-100)
///
/// POST /notificaciones/leer (Bearer)  {"id":int} -> 200 (idempotente)
/// POST /notificaciones/leer-todas (Bearer)       -> 200
///
/// Además, el canal SSE de la sesión (`/auth/sesion/eventos`) entrega el evento
/// `notificacion` con el mismo objeto en cuanto se crea.
class ApiNotificacionesRepository implements NotificacionesRepository {
  ApiNotificacionesRepository(this._api);
  final ApiClient _api;

  @override
  Future<PaginaNotificaciones> listar({
    required int pagina,
    required int tamanio,
  }) async {
    final respuesta = await _api.post(
      '/notificaciones/consultar',
      body: {'pagina': pagina, 'tamanio': tamanio},
    );
    final d = respuesta.dataAsMap;
    final lista = d['lista'] is Map<String, dynamic>
        ? d['lista'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final datos = lista['datos'];
    final elementos = (datos is List ? datos : const [])
        .map(Notificacion.desdeJson)
        .whereType<Notificacion>()
        .toList(growable: false);
    return PaginaNotificaciones(
      elementos: elementos,
      pagina: (lista['pagina'] as num?)?.toInt() ?? pagina,
      totalPaginas: (lista['totalPaginas'] as num?)?.toInt() ?? 1,
      noLeidas: (d['noLeidas'] as num?)?.toInt() ?? 0,
    );
  }

  @override
  Future<void> marcarLeida(int id) async {
    await _api.post('/notificaciones/leer', body: {'id': id});
  }

  @override
  Future<void> marcarTodasLeidas() async {
    await _api.post('/notificaciones/leer-todas');
  }
}
