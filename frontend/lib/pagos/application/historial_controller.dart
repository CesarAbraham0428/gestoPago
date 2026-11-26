import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/compra_historial.dart';
import '../domain/entities/estado_pago.dart';
import '../domain/ports/pagos_repository.dart';

enum EstadoHistorial { inicial, cargando, listo, error }

/// Filtro del historial por estado del pago.
enum FiltroPagos {
  todos(null),
  aprobados(CategoriaPago.aprobado),
  enProceso(CategoriaPago.enProceso),
  rechazados(CategoriaPago.rechazado);

  const FiltroPagos(this.categoria);
  final CategoriaPago? categoria;
}

/// Casos de uso "consultar pagos anteriores" (todos los estados, con paginación
/// incremental) y "buscar un pago por su UPC" (consulta de estado del servicio).

class HistorialController extends ChangeNotifier {
  HistorialController(this._pagos);

  static const _tamanioPagina = 20;
  final PagosRepository _pagos;

  EstadoHistorial _estado = EstadoHistorial.inicial;
  final List<CompraHistorial> _compras = [];
  int _pagina = -1;
  bool _hayMas = false;
  bool _cargandoMas = false;
  bool _recargando = false;
  String? _error;
  String? _errorMas;
  FiltroPagos _filtro = FiltroPagos.todos;

  static final _formatoUpc = RegExp(r'^[A-Z0-9]{15}$');
  bool _buscando = false;
  int _idBusqueda = 0; // descarta respuestas de una búsqueda ya cancelada
  CompraHistorial? _resultado;
  String? _errorBusqueda;

  EstadoHistorial get estado => _estado;
  List<CompraHistorial> get compras => List.unmodifiable(_compras);
  bool get hayMas => _hayMas;
  bool get cargandoMas => _cargandoMas;
  String? get error => _error;
  String? get errorMas => _errorMas;
  FiltroPagos get filtro => _filtro;

  /// Lo cargado hasta ahora, según el filtro elegido.
  List<CompraHistorial> get comprasFiltradas {
    final categoria = _filtro.categoria;
    return categoria == null
        ? compras
        : _compras
              .where((c) => c.categoria == categoria)
              .toList(growable: false);
  }

  bool get buscando => _buscando;
  CompraHistorial? get resultado => _resultado;
  String? get errorBusqueda => _errorBusqueda;
  bool get busquedaActiva =>
      _buscando || _resultado != null || _errorBusqueda != null;

  void filtrar(FiltroPagos filtro) {
    if (_filtro == filtro) return;
    _filtro = filtro;
    notifyListeners();
  }

  /// Consulta el estado de un pago por su UPC (15 letras mayúsculas o dígitos),
  /// sin importar si ya está cargado en la lista.
  Future<void> buscar(String texto) async {
    if (_buscando) return;
    final upc = texto.trim().toUpperCase();
    _resultado = null;
    if (!_formatoUpc.hasMatch(upc)) {
      _errorBusqueda = 'El UPC tiene 15 letras o números.';
      notifyListeners();
      return;
    }
    final id = ++_idBusqueda;
    _buscando = true;
    _errorBusqueda = null;
    notifyListeners();
    try {
      // El servicio confirma que el pago existe; la tarjeta sale del historial, que trae producto y fecha.
      await _pagos.consultarEstado(upc);
      // Si es de una página aún no cargada, se sigue paginando hasta dar con él.
      _errorMas = null;
      while (porReferencia(upc) == null &&
          _hayMas &&
          _estado == EstadoHistorial.listo &&
          _errorMas == null &&
          id == _idBusqueda) {
        await cargarMas();
      }
      if (id != _idBusqueda) return;
      _resultado = porReferencia(upc);
      if (_resultado == null) {
        _errorBusqueda = 'Ese pago no aparece en tus compras.';
      }
    } on AppException catch (e) {
      if (id != _idBusqueda) return;
      _errorBusqueda = e.message;
    } on Object {
      if (id != _idBusqueda) return;
      _errorBusqueda = 'No pudimos consultar el pago. Inténtalo de nuevo.';
    }
    _buscando = false;
    notifyListeners();
  }

  void limpiarBusqueda() {
    if (!busquedaActiva) return;
    _idBusqueda++;
    _buscando = false;
    _resultado = null;
    _errorBusqueda = null;
    notifyListeners();
  }

  CompraHistorial? porReferencia(String upc) {
    for (final c in _compras) {
      if (c.upc == upc) return c;
    }
    return null;
  }

  /// Vuelve a la primera página. Se usa al abrir la pantalla y tras comprar.
  /// Con [silencioso] y compras ya a la vista no se muestra el indicador de carga:
  /// la lista se actualiza cuando llega la respuesta y un fallo la deja como estaba.
  Future<void> recargar({bool silencioso = false}) async {
    if (_recargando) return;
    _recargando = true;
    final enSegundoPlano = silencioso && _estado == EstadoHistorial.listo;
    if (!enSegundoPlano) {
      _estado = EstadoHistorial.cargando;
      _error = null;
      notifyListeners();
    }
    try {
      final primera = await _pagos.historial(
        pagina: 0,
        tamanio: _tamanioPagina,
      );
      _compras
        ..clear()
        ..addAll(primera.elementos);
      _pagina = primera.pagina;
      _hayMas = primera.hayMas;
      _estado = EstadoHistorial.listo;
    } on AppException catch (e) {
      if (!enSegundoPlano) {
        _estado = EstadoHistorial.error;
        _error = e.message;
      }
    } on Object {
      if (!enSegundoPlano) {
        _estado = EstadoHistorial.error;
        _error = 'No pudimos cargar tu historial.';
      }
    }
    _recargando = false;
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
    _recargando = false;
    _error = null;
    _errorMas = null;
    _filtro = FiltroPagos.todos;
    _idBusqueda++;
    _buscando = false;
    _resultado = null;
    _errorBusqueda = null;
    notifyListeners();
  }
}
