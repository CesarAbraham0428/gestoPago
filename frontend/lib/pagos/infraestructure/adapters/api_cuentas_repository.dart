import '../../../core/network/api_client.dart';
import '../../domain/entities/cuenta.dart';
import '../../domain/ports/cuentas_repository.dart';

/// Adaptador HTTP del puerto [CuentasRepository].
///
/// POST /cuentas/consultar (Bearer)  {"activo":true,"pagina":0,"tamanio":20}
///   200 -> datos: {datos:[{id,clienteId,numeroCuenta,saldo,estaActiva,...}],
///                  pagina,tamanoPagina,totalElementos,totalPaginas}
class ApiCuentasRepository implements CuentasRepository {
  ApiCuentasRepository(this._api);
  final ApiClient _api;

  @override
  Future<List<Cuenta>> obtenerActivas() async {
    final respuesta = await _api.post(
      '/cuentas/consultar',
      body: {'activo': true, 'pagina': 0, 'tamanio': 20},
    );
    final lista = respuesta.dataAsMap['datos'];
    if (lista is! List) return const [];
    return lista
        .whereType<Map<String, dynamic>>()
        .map((j) {
          final id = j['id'];
          final numero = j['numeroCuenta'];
          final saldo = j['saldo'];
          if (id is! int || numero is! String || saldo is! num) return null;
          return Cuenta(
            id: id,
            numeroCuenta: numero,
            saldo: saldo.toDouble(),
            activa: j['estaActiva'] == true,
          );
        })
        .whereType<Cuenta>()
        .where((c) => c.activa)
        .toList(growable: false);
  }
}
