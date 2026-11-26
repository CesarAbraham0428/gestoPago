/// Opciones de las listas desplegables de países, nacionalidades y ocupaciones.
/// Viven en la base de datos del servidor; aquí solo se leen.
class Catalogos {
  const Catalogos({
    this.paises = const [],
    this.nacionalidades = const [],
    this.ocupaciones = const [],
  });

  final List<String> paises;
  final List<String> nacionalidades;
  final List<String> ocupaciones;
}

/// Valores que el formulario propone antes de que el usuario elija.
const paisPorOmision = 'México';
const nacionalidadPorOmision = 'Mexicana';

abstract interface class CatalogosRepository {
  Future<Catalogos> obtener();
}
