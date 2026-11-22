import '../../../core/error/app_exception.dart';
import '../../../core/error/mensajes_usuario.dart';
import '../../../core/network/api_client.dart';
import '../../domain/ports/registro_gateway.dart';

/// Adaptador HTTP del puerto [RegistroGateway] (ruta pública, sin token).
///
/// POST /clientes {datos personales, empresa, domicilio, correoElectronico, password}
///   201 -> cliente creado con su cuenta bancaria en saldo 0
///   400 datos inválidos · 409 CURP, RFC o correo ya registrados · 429 demasiados registros
class ApiRegistroGateway implements RegistroGateway {
  ApiRegistroGateway(this._api);
  final ApiClient _api;

  @override
  Future<void> registrar(Map<String, Object?> datos) async {
    try {
      await _api.post('/clientes', body: datos, authenticated: false);
    } on ApiException catch (e) {
      // Un mensaje único: no se confirma cuál de los tres datos ya existe.
      if (e.statusCode == 409) {
        throw const ApiException(409, MensajesUsuario.cuentaYaExiste);
      }
      rethrow;
    }
  }
}
