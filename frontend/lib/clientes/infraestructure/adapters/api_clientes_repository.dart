import '../../../core/error/app_exception.dart';
import '../../../core/error/mensajes_usuario.dart';
import '../../../core/network/api_client.dart';
import '../../../perfil/infraestructure/adapters/api_perfil_repository.dart';
import '../../domain/entities/cliente_admin.dart';
import '../../domain/ports/clientes_repository.dart';

/// Adaptador HTTP del puerto [ClientesRepository].
///
/// GET  /usuarios/me                       -> datos: {clienteId, correo, ...}
/// POST /clientes/consultar {pagina,tamanio<=100,correo?}
///   -> datos: {datos:[ClienteResponse con rol y activo], totalPaginas}
///   Un administrador recibe a todos los clientes; los demás, solo a sí mismos.
/// POST /clientes/desactivar | /clientes/activar {rfc}
///   -> 200; 403 si quien pide no es administrador (activar) · 404 no existe
class ApiClientesRepository implements ClientesRepository {
  ApiClientesRepository(this._api);
  final ApiClient _api;

  static const _tamanioPagina = 100;

  @override
  Future<bool> esAdministrador() async {
    final yo = (await _api.get('/usuarios/me')).dataAsMap;
    final id = yo['clienteId'];
    final correo = yo['correo'];
    if (id is! int || correo is! String) throw _invalida();
    final respuesta = await _api.post(
      '/clientes/consultar',
      body: {'correo': correo, 'pagina': 0, 'tamanio': 5},
    );
    final lista = respuesta.dataAsMap['datos'];
    return lista is List &&
        lista.whereType<Map<String, dynamic>>().any(
          (j) => j['id'] == id && j['rol'] == 'Administrador',
        );
  }

  @override
  Future<List<ClienteAdmin>> listar() async {
    final yo = (await _api.get('/usuarios/me')).dataAsMap['clienteId'];
    if (yo is! int) throw _invalida();
    final clientes = <ClienteAdmin>[];
    var pagina = 0;
    var totalPaginas = 1;
    do {
      final d = (await _api.post(
        '/clientes/consultar',
        body: {'pagina': pagina, 'tamanio': _tamanioPagina},
      )).dataAsMap;
      final lista = d['datos'];
      if (lista is! List) throw _invalida();
      for (final j in lista.whereType<Map<String, dynamic>>()) {
        if (j['id'] != yo) clientes.add(_aCliente(j));
      }
      totalPaginas = (d['totalPaginas'] as num?)?.toInt() ?? 1;
      pagina++;
    } while (pagina < totalPaginas);
    return clientes;
  }

  @override
  Future<void> cambiarEstado({required String rfc, required bool activo}) async {
    await _api.post(
      activo ? '/clientes/activar' : '/clientes/desactivar',
      body: {'rfc': rfc},
    );
  }

  static ClienteAdmin _aCliente(Map<String, dynamic> j) {
    final id = j['id'];
    if (id is! int) throw _invalida();
    return ClienteAdmin(
      id: id,
      perfil: perfilDesdeJson(j),
      activo: j['activo'] == true,
      esAdministrador: j['rol'] == 'Administrador',
      fechaAlta: DateTime.tryParse('${j['fechaCreacion']}'),
    );
  }

  static AppException _invalida() =>
      const ConexionException(MensajesUsuario.respuestaInvalida);
}
