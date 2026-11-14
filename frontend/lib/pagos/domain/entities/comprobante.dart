/// Comprobante de una compra confirmada por el servicio (estado APROBADO).
class Comprobante {
  const Comprobante({
    required this.estado,
    required this.monto,
    required this.comision,
    required this.registradoEn,
    this.idTx,
    this.numAutorizacion,
    this.referencia,
    this.saldoDisponible,
    this.fechaProveedor,
    this.codigoCanje,
    this.leyenda,
  });

  final String estado;
  final double monto;
  final double comision;
  final DateTime registradoEn;
  final int? idTx;
  final String? numAutorizacion;
  final String? referencia;
  final double? saldoDisponible;

  /// Fecha tal como la devuelve el proveedor (texto, sin zona horaria).
  final String? fechaProveedor;

  /// Solo en artículos canjeables; puede ser un código o una URL.
  final String? codigoCanje;
  final String? leyenda;

  bool get estaAprobado => estado == 'APROBADO';
  double get total => monto + comision;
}
