import '../../../pagos/domain/entities/cuenta.dart';

/// Valores que acepta la API para estado civil (con espacio en "Union libre").
const estadosCiviles = [
  'Soltero',
  'Casado',
  'Divorciado',
  'Viudo',
  'Union libre',
];
const sexos = ['Masculino', 'Femenino'];

class Domicilio {
  const Domicilio({
    required this.calle,
    required this.numeroExterior,
    this.numeroInterior,
    required this.colonia,
    required this.municipio,
    required this.estado,
    required this.codigoPostal,
    required this.pais,
  });

  final String calle;
  final String numeroExterior;
  final String? numeroInterior;
  final String colonia;
  final String municipio;
  final String estado;
  final String codigoPostal;
  final String pais;

  Map<String, Object?> aMapa() => {
    'calle': calle,
    'numeroExterior': numeroExterior,
    'numeroInterior': numeroInterior,
    'colonia': colonia,
    'municipio': municipio,
    'estado': estado,
    'codigoPostal': codigoPostal,
    'pais': pais,
  };

  String get lineaCalle => [
    '$calle $numeroExterior',
    if (numeroInterior != null) 'Int. $numeroInterior',
  ].join(', ');

  String get lineaZona => '$colonia, $codigoPostal $municipio, $estado, $pais';
}

/// Datos del cliente autenticado: personales, contacto, empleo, domicilio y
/// cuentas bancarias. CURP y RFC solo se muestran; no se pueden editar.
class Perfil {
  const Perfil({
    required this.primerNombre,
    this.segundoNombre,
    required this.apellidoPaterno,
    required this.apellidoMaterno,
    required this.fechaNacimiento,
    required this.curp,
    required this.rfc,
    required this.sexo,
    required this.nacionalidad,
    this.estadoCivil,
    required this.correoElectronico,
    required this.telefonoMovil,
    this.telefonoAlternativo,
    this.ocupacion,
    this.empresa,
    this.ingresoMensual,
    this.domicilio,
    this.cuentas = const [],
  });

  final String primerNombre;
  final String? segundoNombre;
  final String apellidoPaterno;
  final String apellidoMaterno;
  final DateTime fechaNacimiento;
  final String curp;
  final String rfc;
  final String sexo;
  final String nacionalidad;
  final String? estadoCivil;
  final String correoElectronico;
  final String telefonoMovil;
  final String? telefonoAlternativo;
  // Empleo y domicilio son opcionales en el registro: se capturan después en "Editar perfil".
  final String? ocupacion;
  final String? empresa;
  final double? ingresoMensual;
  final Domicilio? domicilio;
  final List<Cuenta> cuentas;

  String get nombreCompleto => [
    primerNombre,
    segundoNombre,
    apellidoPaterno,
    apellidoMaterno,
  ].whereType<String>().where((s) => s.isNotEmpty).join(' ');

  /// Campos editables con la forma del contrato de actualización de la API.
  Map<String, Object?> aMapaEditable() => {
    'primerNombre': primerNombre,
    'segundoNombre': segundoNombre,
    'apellidoPaterno': apellidoPaterno,
    'apellidoMaterno': apellidoMaterno,
    'fechaNacimiento': fechaIso(fechaNacimiento),
    'sexo': sexo,
    'nacionalidad': nacionalidad,
    'estadoCivil': estadoCivil,
    'correoElectronico': correoElectronico,
    'telefonoMovil': telefonoMovil,
    'telefonoAlternativo': telefonoAlternativo,
    'ocupacion': ocupacion,
    'empresa': empresa,
    'ingresoMensual': ingresoMensual,
    'domicilio': domicilio?.aMapa(),
  };
}

String fechaIso(DateTime f) =>
    '${f.year.toString().padLeft(4, '0')}-${f.month.toString().padLeft(2, '0')}-${f.day.toString().padLeft(2, '0')}';

/// Solo los campos que cambiaron entre [antes] y [despues] (mapas de
/// [Perfil.aMapaEditable]). El domicilio va anidado con sus campos cambiados.
/// Un mapa vacío significa que no hay nada que guardar.
Map<String, Object?> cambiosEntre(
  Map<String, Object?> antes,
  Map<String, Object?> despues,
) {
  final cambios = <String, Object?>{};
  for (final entrada in despues.entries) {
    final previo = antes[entrada.key];
    final nuevo = entrada.value;
    if (previo is Map<String, Object?> && nuevo is Map<String, Object?>) {
      final anidados = cambiosEntre(previo, nuevo);
      if (anidados.isNotEmpty) cambios[entrada.key] = anidados;
    } else if (previo != nuevo) {
      cambios[entrada.key] = nuevo;
    }
  }
  return cambios;
}
