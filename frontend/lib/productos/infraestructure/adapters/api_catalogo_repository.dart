import '../../../core/network/api_client.dart';
import '../../domain/entities/producto.dart';
import '../../domain/ports/catalogo_repository.dart';

/// Adaptador HTTP del puerto [CatalogoRepository].
///
/// GET /productos  (requiere Bearer)
///   200 -> {"codigo":0,"mensaje":"Catálogo consultado",
///           "datos":{"total":N,"productos":[{producto, servicio, idServicio,
///           idProducto, idCatTipoServicio, tipoFront, hasDigitoVerificador,
///           tipoReferencia, precio, showAyuda, legend}]}}
///   401 sesión inválida · 502 proveedor no disponible
class ApiCatalogoRepository implements CatalogoRepository {
  ApiCatalogoRepository(this._api);
  final ApiClient _api;

  @override
  Future<List<Producto>> obtenerCatalogo() async {
    final respuesta = await _api.get(
      '/productos',
      timeout: const Duration(seconds: 30),
    );
    final lista = respuesta.dataAsMap['productos'];
    if (lista is! List) return const [];
    return lista
        .whereType<Map<String, dynamic>>()
        .map(_aProducto)
        .whereType<Producto>()
        .toList(growable: false);
  }

  /// Descarta registros sin identificadores en lugar de romper todo el catálogo.
  Producto? _aProducto(Map<String, dynamic> json) {
    final idProducto = json['idProducto'];
    final idServicio = json['idServicio'];
    final tipoFront = json['tipoFront'];
    if (idProducto is! int || idServicio is! int || tipoFront is! int) {
      return null;
    }
    String texto(Object? v, String porDefecto) {
      final s = v is String ? v.trim() : '';
      return s.isEmpty ? porDefecto : s;
    }

    return Producto(
      nombre: texto(json['producto'], 'Producto'),
      servicio: texto(json['servicio'], 'Servicio'),
      idProducto: idProducto,
      idServicio: idServicio,
      tipoFront: tipoFront,
      idCatTipoServicio: json['idCatTipoServicio'] as int?,
      precio: (json['precio'] as num?)?.toDouble(),
      tipoReferencia: (json['tipoReferencia'] as String?)?.trim(),
      leyenda: (json['legend'] as String?)?.trim(),
      tieneDigitoVerificador: json['hasDigitoVerificador'] == true,
    );
  }
}
