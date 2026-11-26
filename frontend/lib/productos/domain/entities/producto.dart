/// Qué tipo de operación permite un producto del catálogo.
enum TipoCompra {
  /// `tipoFront 1`: precio fijo del catálogo; se paga a un número de celular.
  recarga,

  /// `tipoFront 2`: el cliente indica la referencia y el monto; el precio del
  /// catálogo es la comisión que se suma al cargo.
  pagoServicio,

  /// Consultas de adeudo u otros productos que la API no permite pagar.
  noDisponible,
}

/// Tipos de servicio que entrega la API en `idCatTipoServicio`
/// (docs/pagosGestoPago.md §4.3). Son los que existen en la tabla `producto`.
const _tiposServicio = {
  1: 'Movistar',
  2: 'Telcel',
  3: 'AT&T / Iusacell',
  4: 'Unefon',
  5: 'Pago de servicios',
  6: 'Nextel',
  7: 'Impuestos y gobierno',
  8: 'Productos financieros',
  9: 'Productos por catálogo',
  10: 'Servicios de prepago',
  11: 'Entretenimiento',
  12: 'Virgin',
  13: 'Otras recargas',
  14: 'Seguros',
  15: 'Derechos de agua',
  16: 'Juegos y sorteos',
  17: 'MazTiempo',
  18: 'Autopistas y transportes',
  19: 'Internet de prepago',
};

/// Tipo 0 agrupa los productos que llegan sin `idCatTipoServicio`.
String etiquetaTipoServicio(int tipo) =>
    tipo == 0 ? 'Otros' : _tiposServicio[tipo] ?? 'Tipo $tipo';

class Producto {
  const Producto({
    required this.nombre,
    required this.servicio,
    required this.idProducto,
    required this.idServicio,
    required this.tipoFront,
    this.idCatTipoServicio,
    this.precio,
    this.tipoReferencia,
    this.leyenda,
    this.tieneDigitoVerificador = false,
  });

  final String nombre;
  final String servicio;
  final int idProducto;
  final int idServicio;
  final int tipoFront;
  final int? idCatTipoServicio;
  final double? precio;
  final String? tipoReferencia;
  final String? leyenda;
  final bool tieneDigitoVerificador;

  /// Identificador estable para rutas y selección.
  String get clave => '$idProducto-$idServicio';

  /// Tarjetas de regalo y códigos canjeables: no piden número de celular.
  bool get esCanjeable =>
      tipoFront == 1 && (idCatTipoServicio == 10 || idCatTipoServicio == 11);

  TipoCompra get tipoCompra {
    if (tipoFront == 1) {
      return (precio ?? 0) > 0 ? TipoCompra.recarga : TipoCompra.noDisponible;
    }
    if (tipoFront == 2) return TipoCompra.pagoServicio;
    return TipoCompra.noDisponible;
  }

  bool get disponibleParaCompra => tipoCompra != TipoCompra.noDisponible;

  /// Tipo de servicio para filtrar el catálogo (0 si la API no lo informa).
  int get tipoServicio => idCatTipoServicio ?? 0;

  /// Monto que se cobra por el producto cuando es de precio fijo.
  double? get montoFijo => tipoCompra == TipoCompra.recarga ? precio : null;

  /// Comisión que se suma al pago de un servicio.
  double get comision =>
      tipoCompra == TipoCompra.pagoServicio ? (precio ?? 0) : 0;
}
