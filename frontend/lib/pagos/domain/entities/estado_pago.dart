/// Estado de un pago consultado por su referencia (UPC).
class EstadoPago {
  const EstadoPago({
    required this.upc,
    required this.estado,
    required this.mensaje,
    this.monto,
    this.comision,
    this.referencia,
    this.idTx,
    this.numAutorizacion,
  });

  final String upc;

  /// PENDIENTE, EN_REVISION, APROBADO o RECHAZADO.
  final String estado;
  final String mensaje;
  final double? monto;
  final double? comision;
  final String? referencia;
  final int? idTx;
  final String? numAutorizacion;

  CategoriaPago get categoria => categoriaDeEstado(estado);

  bool get aprobado => estado == 'APROBADO';
  bool get rechazado => estado == 'RECHAZADO';
  bool get enProceso => estado == 'PENDIENTE' || estado == 'EN_REVISION';
}

/// Los cuatro estados del servicio agrupados como los ve el usuario:
/// PENDIENTE y EN_REVISION son lo mismo para él ("en proceso").
enum CategoriaPago { aprobado, enProceso, rechazado }

CategoriaPago categoriaDeEstado(String estado) => switch (estado) {
  'APROBADO' => CategoriaPago.aprobado,
  'RECHAZADO' => CategoriaPago.rechazado,
  _ => CategoriaPago.enProceso,
};
