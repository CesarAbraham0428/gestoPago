import '../../../core/network/api_client.dart';
import '../../domain/ports/registro_gateway.dart';

/// Adaptador HTTP del puerto [RegistroGateway] (ruta pública, sin token).
///
/// POST /clientes {datos personales, empresa, domicilio, correoElectronico, password}
///   201 -> cliente creado con su cuenta bancaria en saldo 0
///   400 datos inválidos · 409 CURP, RFC, teléfono o correo ya registrados (el mensaje
///   nombra cuáles) · 429 demasiados registros
///
/// POST /clientes/disponibilidad {curp?, rfc?, telefonoMovil?, correoElectronico?}
///   200 -> {duplicados: [nombres de los campos que ya tienen cuenta]}
class ApiRegistroGateway implements RegistroGateway {
  ApiRegistroGateway(this._api);
  final ApiClient _api;

  @override
  Future<void> registrar(Map<String, Object?> datos) async {
    await _api.post('/clientes', body: datos, authenticated: false);
  }

  @override
  Future<List<String>> repetidos(Map<String, String> datos) async {
    final r = await _api.post(
      '/clientes/disponibilidad',
      body: datos,
      authenticated: false,
    );
    final lista = r.dataAsMap['duplicados'];
    return lista is List ? [for (final n in lista) n.toString()] : const [];
  }
}
