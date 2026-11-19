import '../../../core/error/app_exception.dart';
import '../../../core/error/mensajes_usuario.dart';
import '../../../core/network/api_client.dart';
import '../../../pagos/domain/entities/cuenta.dart';
import '../../domain/entities/perfil.dart';
import '../../domain/ports/perfil_repository.dart';

/// Adaptador HTTP del puerto [PerfilRepository].
///
/// GET   /usuarios/me                 -> datos: {clienteId, correo, ...}
/// POST  /clientes/consultar {correo, pagina:0, tamanio:5}
///   -> datos: {datos:[ClienteResponse con domicilio y cuentas]}
/// PATCH /clientes {identificacion:{rfc}, cambios:{...solo campos cambiados}}
///   -> datos: ClienteResponse
/// PUT   /cuentas/pin {numeroCuenta, pinActual?, pinNuevo}
///
/// Se filtra por el clienteId de /usuarios/me porque a un administrador la
/// consulta le devuelve todos los clientes.
class ApiPerfilRepository implements PerfilRepository {
  ApiPerfilRepository(this._api);
  final ApiClient _api;

  @override
  Future<Perfil> obtener() async {
    final yo = (await _api.get('/usuarios/me')).dataAsMap;
    final clienteId = yo['clienteId'];
    final correo = yo['correo'];
    if (clienteId is! int || correo is! String) throw _invalida();
    final respuesta = await _api.post(
      '/clientes/consultar',
      body: {'correo': correo, 'pagina': 0, 'tamanio': 5},
    );
    final lista = respuesta.dataAsMap['datos'];
    if (lista is List) {
      for (final j in lista.whereType<Map<String, dynamic>>()) {
        if (j['id'] == clienteId) return perfilDesdeJson(j);
      }
    }
    throw _invalida();
  }

  @override
  Future<Perfil> actualizar({
    required String rfc,
    required Map<String, Object?> cambios,
  }) async {
    final respuesta = await _api.patch(
      '/clientes',
      body: {
        'identificacion': {'rfc': rfc},
        'cambios': cambios,
      },
    );
    return perfilDesdeJson(respuesta.dataAsMap);
  }

  @override
  Future<void> cambiarPin({
    required String numeroCuenta,
    String? pinActual,
    required String pinNuevo,
  }) async {
    await _api.put(
      '/cuentas/pin',
      body: {
        'numeroCuenta': numeroCuenta,
        'pinActual': ?pinActual,
        'pinNuevo': pinNuevo,
      },
    );
  }

  static AppException _invalida() =>
      const ConexionException(MensajesUsuario.respuestaInvalida);
}

/// Convierte un ClienteResponse en [Perfil]. Lanza si faltan campos obligatorios.
Perfil perfilDesdeJson(Map<String, dynamic> j) {
  String texto(Map<String, dynamic> m, String k) {
    final v = m[k];
    if (v is! String) throw ApiPerfilRepository._invalida();
    return v;
  }

  String? opcional(Map<String, dynamic> m, String k) {
    final v = m[k];
    return v is String && v.isNotEmpty ? v : null;
  }

  final dom = j['domicilio'];
  final fecha = DateTime.tryParse(j['fechaNacimiento']?.toString() ?? '');
  final ingreso = j['ingresoMensual'];
  if (dom is! Map<String, dynamic> || fecha == null || ingreso is! num) {
    throw ApiPerfilRepository._invalida();
  }
  final cuentas = j['cuentas'];
  return Perfil(
    primerNombre: texto(j, 'primerNombre'),
    segundoNombre: opcional(j, 'segundoNombre'),
    apellidoPaterno: texto(j, 'apellidoPaterno'),
    apellidoMaterno: texto(j, 'apellidoMaterno'),
    fechaNacimiento: fecha,
    curp: texto(j, 'curp'),
    rfc: texto(j, 'rfc'),
    sexo: texto(j, 'sexo'),
    nacionalidad: texto(j, 'nacionalidad'),
    estadoCivil: texto(j, 'estadoCivil'),
    correoElectronico: texto(j, 'correoElectronico'),
    telefonoMovil: texto(j, 'telefonoMovil'),
    telefonoAlternativo: opcional(j, 'telefonoAlternativo'),
    ocupacion: texto(j, 'ocupacion'),
    empresa: texto(j, 'empresa'),
    ingresoMensual: ingreso.toDouble(),
    domicilio: Domicilio(
      calle: texto(dom, 'calle'),
      numeroExterior: texto(dom, 'numeroExterior'),
      numeroInterior: opcional(dom, 'numeroInterior'),
      colonia: texto(dom, 'colonia'),
      municipio: texto(dom, 'municipio'),
      estado: texto(dom, 'estado'),
      codigoPostal: texto(dom, 'codigoPostal'),
      pais: texto(dom, 'pais'),
    ),
    cuentas: cuentas is List
        ? cuentas
              .whereType<Map<String, dynamic>>()
              .map((c) {
                final id = c['id'];
                final numero = c['numeroCuenta'];
                final saldo = c['saldo'];
                if (id is! int || numero is! String || saldo is! num) return null;
                return Cuenta(
                  id: id,
                  numeroCuenta: numero,
                  saldo: saldo.toDouble(),
                  activa: c['estaActiva'] == true,
                );
              })
              .whereType<Cuenta>()
              .toList(growable: false)
        : const [],
  );
}
