import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/producto.dart';
import '../domain/ports/catalogo_repository.dart';

enum EstadoCatalogo { inicial, cargando, listo, error }

/// Caso de uso "consultar catálogo" con búsqueda y filtros en memoria.
class CatalogoController extends ChangeNotifier {
  CatalogoController(this._repositorio);

  final CatalogoRepository _repositorio;

  EstadoCatalogo _estado = EstadoCatalogo.inicial;
  List<Producto> _todos = const [];
  List<Producto> _visibles = const [];
  String _busqueda = '';
  CategoriaProducto? _categoria;
  bool _soloDisponibles = true;
  String? _error;

  EstadoCatalogo get estado => _estado;
  String? get error => _error;
  List<Producto> get visibles => _visibles;
  String get busqueda => _busqueda;
  CategoriaProducto? get categoria => _categoria;
  bool get soloDisponibles => _soloDisponibles;
  int get total => _todos.length;

  /// Categorías que tienen productos, en orden de presentación.
  List<CategoriaProducto> get categoriasPresentes {
    final presentes = _todos
        .where((p) => !_soloDisponibles || p.disponibleParaCompra)
        .map((p) => p.categoria)
        .toSet();
    return CategoriaProducto.values.where(presentes.contains).toList();
  }

  Producto? porClave(String clave) {
    for (final p in _todos) {
      if (p.clave == clave) return p;
    }
    return null;
  }

  Future<void> cargar({bool forzar = false}) async {
    if (_estado == EstadoCatalogo.cargando) return;
    if (_estado == EstadoCatalogo.listo && !forzar) return;
    _estado = EstadoCatalogo.cargando;
    _error = null;
    notifyListeners();
    try {
      _todos = await _repositorio.obtenerCatalogo();
      _estado = EstadoCatalogo.listo;
      _aplicarFiltros();
    } on AppException catch (e) {
      _estado = EstadoCatalogo.error;
      _error = e.message;
    } on Object {
      _estado = EstadoCatalogo.error;
      _error = 'No pudimos cargar el catálogo.';
    }
    notifyListeners();
  }

  void buscar(String texto) {
    _busqueda = texto;
    _aplicarFiltros();
    notifyListeners();
  }

  void filtrarCategoria(CategoriaProducto? categoria) {
    _categoria = categoria;
    _aplicarFiltros();
    notifyListeners();
  }

  void alternarSoloDisponibles(bool valor) {
    _soloDisponibles = valor;
    if (_categoria != null && !categoriasPresentes.contains(_categoria)) {
      _categoria = null;
    }
    _aplicarFiltros();
    notifyListeners();
  }

  /// Se llama al cerrar sesión para no mostrar datos al siguiente usuario.
  void limpiar() {
    _estado = EstadoCatalogo.inicial;
    _todos = const [];
    _visibles = const [];
    _busqueda = '';
    _categoria = null;
    _error = null;
    notifyListeners();
  }

  void _aplicarFiltros() {
    final consulta = _normalizar(_busqueda);
    final lista = _todos.where((p) {
      if (_soloDisponibles && !p.disponibleParaCompra) return false;
      if (_categoria != null && p.categoria != _categoria) return false;
      if (consulta.isEmpty) return true;
      return _normalizar('${p.nombre} ${p.servicio}').contains(consulta);
    }).toList();
    lista.sort((a, b) {
      final porServicio = a.servicio.toLowerCase().compareTo(b.servicio.toLowerCase());
      return porServicio != 0
          ? porServicio
          : a.nombre.toLowerCase().compareTo(b.nombre.toLowerCase());
    });
    _visibles = lista;
  }

  static const _acentos = {
    'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ü': 'u', 'ñ': 'n',
  };

  String _normalizar(String texto) {
    final buffer = StringBuffer();
    for (final c in texto.toLowerCase().trim().split('')) {
      buffer.write(_acentos[c] ?? c);
    }
    return buffer.toString();
  }
}
