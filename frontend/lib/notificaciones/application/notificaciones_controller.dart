import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../../authentication/domain/entities/evento_sesion.dart';
import '../../core/error/app_exception.dart';
import '../domain/entities/notificacion.dart';
import '../domain/ports/notificaciones_repository.dart';

enum EstadoNotificaciones { inicial, cargando, listo, error }

/// Bandeja de notificaciones: carga paginada, marcado como leídas y avisos en
/// tiempo real. Escucha el canal de eventos de la sesión: `conectado` (al abrir
/// o reabrir el canal, recarga por si se perdió algún aviso) y `notificacion`.
class NotificacionesController extends ChangeNotifier {
  NotificacionesController(this._repositorio, {Stream<EventoSesion>? eventos}) {
    _eventos = eventos?.listen(_alEvento);
  }

  static const _tamanioPagina = 20;
  final NotificacionesRepository _repositorio;
  StreamSubscription<EventoSesion>? _eventos;
  final _nuevas = StreamController<Notificacion>.broadcast();

  EstadoNotificaciones _estado = EstadoNotificaciones.inicial;
  final List<Notificacion> _items = [];
  int _noLeidas = 0;
  int _pagina = -1;
  bool _hayMas = false;
  bool _cargandoMas = false;
  bool _soloNoLeidas = false;
  String? _error;
  String? _errorMas;

  EstadoNotificaciones get estado => _estado;
  List<Notificacion> get items => List.unmodifiable(_items);
  int get noLeidas => _noLeidas;
  bool get hayMas => _hayMas;
  bool get cargandoMas => _cargandoMas;
  bool get soloNoLeidas => _soloNoLeidas;
  String? get error => _error;
  String? get errorMas => _errorMas;

  /// Lo cargado hasta ahora, según el filtro elegido.
  List<Notificacion> get visibles => _soloNoLeidas
      ? _items.where((n) => !n.leida).toList(growable: false)
      : items;

  /// Avisos que llegan en tiempo real (no los de la carga inicial).
  Stream<Notificacion> get nuevas => _nuevas.stream;

  void filtrarNoLeidas(bool valor) {
    if (_soloNoLeidas == valor) return;
    _soloNoLeidas = valor;
    notifyListeners();
  }

  /// Vuelve a la primera página. [silencioso] no muestra el indicador de carga
  /// (para refrescar sin parpadeo al reconectar el canal).
  Future<void> recargar({bool silencioso = false}) async {
    if (_estado == EstadoNotificaciones.cargando) return;
    if (!silencioso || _estado != EstadoNotificaciones.listo) {
      _estado = EstadoNotificaciones.cargando;
      _error = null;
      notifyListeners();
    }
    try {
      final primera = await _repositorio.listar(
        pagina: 0,
        tamanio: _tamanioPagina,
      );
      _items
        ..clear()
        ..addAll(primera.elementos);
      _noLeidas = primera.noLeidas;
      _pagina = primera.pagina;
      _hayMas = primera.hayMas;
      _estado = EstadoNotificaciones.listo;
    } on AppException catch (e) {
      _error = e.message;
      _estado = EstadoNotificaciones.error;
    } on Object {
      _error = 'No pudimos cargar tus notificaciones.';
      _estado = EstadoNotificaciones.error;
    }
    notifyListeners();
  }

  Future<void> cargarMas() async {
    if (!_hayMas || _cargandoMas || _estado != EstadoNotificaciones.listo) {
      return;
    }
    _cargandoMas = true;
    _errorMas = null;
    notifyListeners();
    try {
      final siguiente = await _repositorio.listar(
        pagina: _pagina + 1,
        tamanio: _tamanioPagina,
      );
      final conocidas = _items.map((n) => n.id).toSet();
      _items.addAll(
        siguiente.elementos.where((n) => !conocidas.contains(n.id)),
      );
      _noLeidas = siguiente.noLeidas;
      _pagina = siguiente.pagina;
      _hayMas = siguiente.hayMas;
    } on AppException catch (e) {
      _errorMas = e.message;
    } on Object {
      _errorMas = 'No pudimos cargar más notificaciones.';
    }
    _cargandoMas = false;
    notifyListeners();
  }

  /// Marca como leída al instante y confirma con el servidor; si falla, vuelve a
  /// consultar para no mostrar un estado que el servidor no tiene.
  Future<void> marcarLeida(Notificacion notificacion) async {
    final i = _items.indexWhere((n) => n.id == notificacion.id);
    if (i < 0 || _items[i].leida) return;
    _items[i] = _items[i].marcadaLeida();
    if (_noLeidas > 0) _noLeidas--;
    notifyListeners();
    try {
      await _repositorio.marcarLeida(notificacion.id);
    } on Object {
      unawaited(recargar(silencioso: true));
    }
  }

  Future<void> marcarTodasLeidas() async {
    if (_noLeidas == 0 && _items.every((n) => n.leida)) return;
    for (var i = 0; i < _items.length; i++) {
      _items[i] = _items[i].marcadaLeida();
    }
    _noLeidas = 0;
    notifyListeners();
    try {
      await _repositorio.marcarTodasLeidas();
    } on Object {
      unawaited(recargar(silencioso: true));
    }
  }

  void _alEvento(EventoSesion evento) {
    switch (evento.nombre) {
      case 'conectado':
        unawaited(recargar(silencioso: true));
      case 'notificacion':
        final Object? json;
        try {
          json = jsonDecode(evento.datos);
        } on FormatException {
          return;
        }
        final nueva = Notificacion.desdeJson(json);
        if (nueva == null || _items.any((n) => n.id == nueva.id)) return;
        _items.insert(0, nueva);
        if (!nueva.leida) _noLeidas++;
        notifyListeners();
        _nuevas.add(nueva);
    }
  }

  /// Al cerrar sesión se descartan los avisos del usuario anterior.
  void limpiar() {
    _estado = EstadoNotificaciones.inicial;
    _items.clear();
    _noLeidas = 0;
    _pagina = -1;
    _hayMas = false;
    _cargandoMas = false;
    _soloNoLeidas = false;
    _error = null;
    _errorMas = null;
    notifyListeners();
  }

  @override
  void dispose() {
    unawaited(_eventos?.cancel());
    unawaited(_nuevas.close());
    super.dispose();
  }
}
