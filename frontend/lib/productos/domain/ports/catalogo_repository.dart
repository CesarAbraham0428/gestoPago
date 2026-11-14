import '../entities/producto.dart';

/// Puerto de salida: fuente del catálogo de productos.
abstract interface class CatalogoRepository {
  Future<List<Producto>> obtenerCatalogo();
}
