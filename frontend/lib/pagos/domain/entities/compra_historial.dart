/// Compra aprobada del usuario, tal como la lista el historial.
class CompraHistorial {
  const CompraHistorial({
    required this.upc,
    required this.monto,
    required this.comision,
    required this.total,
    required this.fecha,
    this.producto,
    this.servicio,
    this.referencia,
    this.telefono,
    this.idTx,
    this.numAutorizacion,
  });

  /// Referencia única de la operación.
  final String upc;
  final String? producto;
  final String? servicio;
  final double monto;
  final double comision;
  final double total;
  final DateTime fecha;
  final String? referencia;
  final String? telefono;
  final int? idTx;
  final String? numAutorizacion;

  String get titulo => producto ?? 'Compra';
}

class Pagina<T> {
  const Pagina({
    required this.elementos,
    required this.pagina,
    required this.totalPaginas,
    required this.totalElementos,
  });

  final List<T> elementos;
  final int pagina;
  final int totalPaginas;
  final int totalElementos;

  bool get hayMas => pagina + 1 < totalPaginas;
}
