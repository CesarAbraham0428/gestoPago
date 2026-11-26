import '../network/api_client.dart';
import 'catalogos.dart';

/// Adaptador HTTP de [CatalogosRepository] (ruta pública).
///
/// GET /catalogos
///   200 -> datos: {paises: [..], nacionalidades: [..], ocupaciones: [..]} (ordenadas)
///
/// Las listas casi no cambian: se piden una sola vez y se reutilizan; si la
/// petición falla se vuelve a intentar en la siguiente consulta.
class ApiCatalogosRepository implements CatalogosRepository {
  ApiCatalogosRepository(this._api);
  final ApiClient _api;
  Future<Catalogos>? _pedido;

  @override
  Future<Catalogos> obtener() => _pedido ??= _pedir();

  Future<Catalogos> _pedir() async {
    try {
      final datos = (await _api.get('/catalogos')).dataAsMap;
      List<String> lista(String clave) {
        final valor = datos[clave];
        return valor is List ? [for (final v in valor) v.toString()] : const [];
      }

      return Catalogos(
        paises: lista('paises'),
        nacionalidades: lista('nacionalidades'),
        ocupaciones: lista('ocupaciones'),
      );
    } catch (_) {
      _pedido = null; // un fallo no se recuerda
      rethrow;
    }
  }
}
