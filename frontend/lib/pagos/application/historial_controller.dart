import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/compra_historial.dart';
import '../domain/ports/pagos_repository.dart';

enum EstadoHistorial { inicial, cargando, listo, error }

/// Caso de uso "consultar compras anteriores" con paginación incremental.
class HistorialController extends ChangeNotifier {
  HistorialController(this._pagos);

  static const _tamanioPagina = 20;
  final PagosRepository _pagos;

  EstadoHistorial _estado = EstadoHistorial.inicial;
  final List<CompraHistorial> _compras = [];
  int _pagina = -1;
  bool _hayMas = false;
  bool _cargandoMas = false;
  String? _error;
  String? _errorMas;

  EstadoHistorial get estado => _estado;
  List<CompraHistorial> get compras => List.unmodifiable(_compras);
  bool get hayMas => _hayMas;
  bool get cargandoMas => _cargandoMas;
  String? get error => _error;
  String? get errorMas => _errorMas;

  CompraHistorial? porReferencia(String upc) {
    for (final c in _compras) {
      if (c.upc == upc) return c;
    }
    return null;
  }

  /// Vuelve a la primera página. Se usa al abrir la pantalla y tras comprar.
  Future<void> recargar() async {
    if (_estado == EstadoHistorial.cargando) return;
    _estado = EstadoHistorial.cargando;
    _error = null;
    notifyListeners();
    try {
      final primera = await _pagos.historial(pagina: 0, tamanio: _tamanioPagina);
      _compras
        ..clear()
        ..addAll(primera.elementos);
      _pagina = primera.pagina;
      _hayMas = primera.hayMas;
      _estado = EstadoHistorial.listo;
    } on AppException catch (e) {
      _estado = EstadoHistorial.error;
      _error = e.message;
    } on Object {
      _estado = EstadoHistorial.error;
      _error = 'No pudimos cargar tu historial.';
    }
    notifyListeners();
  }

  Future<void> cargarMas() async {
    if (!_hayMas || _cargandoMas || _estado != EstadoHistorial.listo) return;
    _cargandoMas = true;
    _errorMas = null;
    notifyListeners();
    try {
      final siguiente = await _pagos.historial(
        pagina: _pagina + 1,
        tamanio: _tamanioPagina,
      );
      _compras.addAll(siguiente.elementos);
      _pagina = siguiente.pagina;
      _hayMas = siguiente.hayMas;
    } on AppException catch (e) {
      _errorMas = e.message;
    } on Object {
      _errorMas = 'No pudimos cargar más compras.';
    }
    _cargandoMas = false;
    notifyListeners();
  }

  void limpiar() {
    _estado = EstadoHistorial.inicial;
    _compras.clear();
    _pagina = -1;
    _hayMas = false;
    _error = null;
    _errorMas = null;
    notifyListeners();
  }
}
