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

/// Agrupación para navegar el catálogo. La API no entrega nombres de
/// categoría, así que se derivan de `tipoFront` e `idCatTipoServicio`.
enum CategoriaProducto {
  recargas('Recargas'),
  tarjetas('Tarjetas y códigos'),
  servicios('Pago de servicios'),
  otros('Otros');

  const CategoriaProducto(this.etiqueta);
  final String etiqueta;
}

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

  CategoriaProducto get categoria {
    if (!disponibleParaCompra) return CategoriaProducto.otros;
    if (esCanjeable) return CategoriaProducto.tarjetas;
    return tipoCompra == TipoCompra.recarga
        ? CategoriaProducto.recargas
        : CategoriaProducto.servicios;
  }

  /// Monto que se cobra por el producto cuando es de precio fijo.
  double? get montoFijo => tipoCompra == TipoCompra.recarga ? precio : null;

  /// Comisión que se suma al pago de un servicio.
  double get comision =>
      tipoCompra == TipoCompra.pagoServicio ? (precio ?? 0) : 0;
}
