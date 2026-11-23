/// Puerto de salida para crear la cuenta de un usuario nuevo. El servidor crea
/// el cliente, su domicilio, su acceso y su cuenta bancaria con saldo cero; las
/// reglas reales (duplicados, edad, contraseña) se validan allá.
abstract interface class RegistroGateway {
  /// Registra al cliente con el cuerpo que espera `POST /clientes`.
  Future<void> registrar(Map<String, Object?> datos);

  /// Pregunta cuáles de los datos únicos ya pertenecen a otra cuenta. [datos]
  /// usa los nombres de la API (`curp`, `rfc`, `telefonoMovil`,
  /// `correoElectronico`); devuelve los nombres de los repetidos.
  Future<List<String>> repetidos(Map<String, String> datos);
}
