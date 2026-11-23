import '../../../core/error/app_exception.dart';
import '../../../core/error/mensajes_usuario.dart';
import '../../../core/network/api_client.dart';
import '../../domain/entities/desafio_verificacion.dart';
import '../../domain/entities/sesion.dart';
import '../../domain/ports/autenticacion_gateway.dart';

/// Adaptador HTTP del puerto [AutenticacionGateway]. Acceso en dos pasos.
///
/// POST /auth/login   {"correo","password"}
///   200 -> datos: {desafioId, expiraEnMs, enrolamiento}  (el código lo da la app de autenticación;
///          `enrolamiento` = {secreto, otpauthUri} solo mientras la cuenta no tiene la app enlazada)
///   400 datos inválidos · 401 credenciales incorrectas · 403 usuario inactivo
///   429 demasiados intentos
///
/// POST /auth/verificar   {"desafioId","codigo":"6 dígitos","cerrarSesionAnterior":bool}
///   200 -> datos: {token, tipo, expiraEnMs, usuario, nombreCompleto}
///   400 datos inválidos · 401 código incorrecto, vencido o con demasiados intentos
///   403 usuario inactivo · 409 ya hay otra sesión activa (reenviar con cerrarSesionAnterior=true)
///   429 demasiados intentos
///
/// POST /auth/logout (Bearer)
///   200 -> la sesión deja de ser válida de inmediato · 401 la sesión ya no estaba activa
class ApiAutenticacionGateway implements AutenticacionGateway {
  ApiAutenticacionGateway(this._api);
  final ApiClient _api;

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async {
    final ApiResponse respuesta;
    try {
      respuesta = await _api.post(
        '/auth/login',
        body: {'correo': correo, 'password': password},
        authenticated: false,
      );
    } on ApiException catch (e) {
      // Un correo inexistente y una contraseña errónea se ven igual: el
      // mensaje no confirma si la cuenta existe.
      throw switch (e.statusCode) {
        400 || 401 || 404 => ApiException(
          e.statusCode,
          MensajesUsuario.credencialesIncorrectas,
        ),
        403 => ApiException(e.statusCode, MensajesUsuario.cuentaNoDisponible),
        _ => e,
      };
    }
    final datos = respuesta.dataAsMap;
    final id = datos['desafioId'];
    final vigenciaMs = datos['expiraEnMs'];
    if (id is! String || id.isEmpty || vigenciaMs is! num) {
      throw const ConexionException(MensajesUsuario.respuestaInvalida);
    }
    return DesafioVerificacion(
      desafioId: id,
      correo: correo,
      vigencia: Duration(milliseconds: vigenciaMs.toInt()),
      enrolamiento: _enrolamientoDe(datos['enrolamiento']),
    );
  }

  static EnrolamientoTotp? _enrolamientoDe(Object? crudo) {
    if (crudo == null) return null;
    final secreto = crudo is Map ? crudo['secreto'] : null;
    final uri = crudo is Map ? crudo['otpauthUri'] : null;
    if (secreto is! String ||
        secreto.isEmpty ||
        uri is! String ||
        uri.isEmpty) {
      throw const ConexionException(MensajesUsuario.respuestaInvalida);
    }
    return EnrolamientoTotp(secreto: secreto, otpauthUri: uri);
  }

  @override
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  }) async {
    final ApiResponse respuesta;
    try {
      respuesta = await _api.post(
        '/auth/verificar',
        body: {
          'desafioId': desafio.desafioId,
          'codigo': codigo,
          'cerrarSesionAnterior': cerrarSesionAnterior,
        },
        authenticated: false,
      );
    } on ApiException catch (e) {
      if (e.statusCode == 409) throw SesionActivaException(e.message);
      throw switch (e.statusCode) {
        401 when e.message.contains('Demasiados') => const ApiException(
          401,
          MensajesUsuario.demasiadosCodigos,
        ),
        400 ||
        401 ||
        404 => ApiException(e.statusCode, MensajesUsuario.codigoIncorrecto),
        403 => ApiException(e.statusCode, MensajesUsuario.cuentaNoDisponible),
        _ => e,
      };
    }
    final datos = respuesta.dataAsMap;
    final token = datos['token'];
    final expiraEnMs = datos['expiraEnMs'];
    if (token is! String || token.isEmpty || expiraEnMs is! num) {
      throw const ConexionException(MensajesUsuario.respuestaInvalida);
    }
    return Sesion(
      token: token,
      correo: (datos['usuario'] as String?) ?? desafio.correo,
      nombreCompleto: (datos['nombreCompleto'] as String?) ?? '',
      expiraEn: DateTime.now().add(Duration(milliseconds: expiraEnMs.toInt())),
    );
  }

  @override
  Future<void> cerrarSesion() async {
    await _api.post('/auth/logout', timeout: const Duration(seconds: 8));
  }
}
