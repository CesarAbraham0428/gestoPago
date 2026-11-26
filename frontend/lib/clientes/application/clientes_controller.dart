import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/cliente_admin.dart';
import '../domain/ports/clientes_repository.dart';

enum EstadoClientes { inicial, cargando, listo, error }

enum FiltroEstadoCliente { todos, activos, inactivos }

/// Casos de uso del panel de clientes: listar, buscar, filtrar y activar o
/// desactivar la cuenta de un cliente. También sabe si el usuario es
/// administrador, que es lo que decide si el menú ofrece la sección.
class ClientesController extends ChangeNotifier {
  ClientesController(this._repositorio);

  final ClientesRepository _repositorio;

  EstadoClientes _estado = EstadoClientes.inicial;
  List<ClienteAdmin> _clientes = const [];
  String? _error;
  bool? _esAdministrador;
  String _texto = '';
  FiltroEstadoCliente _filtro = FiltroEstadoCliente.todos;
  int? _cambiandoId;

  EstadoClientes get estado => _estado;
  String? get error => _error;
  FiltroEstadoCliente get filtro => _filtro;

  /// `null` mientras no se sabe (o si la consulta falló): el menú no lo ofrece.
  bool? get esAdministrador => _esAdministrador;

  /// Cliente cuyo cambio de estado está en curso.
  int? get cambiandoId => _cambiandoId;

  int get total => _clientes.length;

  int get activos => _clientes.where((c) => c.activo).length;

  ClienteAdmin? porId(int id) {
    for (final c in _clientes) {
      if (c.id == id) return c;
    }
    return null;
  }

  /// Clientes que cumplen la búsqueda y el filtro, ordenados por nombre.
  List<ClienteAdmin> get visibles {
    final texto = _texto.trim().toLowerCase();
    final lista = _clientes.where((c) {
      if (_filtro == FiltroEstadoCliente.activos && !c.activo) return false;
      if (_filtro == FiltroEstadoCliente.inactivos && c.activo) return false;
      return texto.isEmpty ||
          c.nombre.toLowerCase().contains(texto) ||
          c.correo.toLowerCase().contains(texto) ||
          c.perfil.rfc.toLowerCase().contains(texto);
    }).toList();
    lista.sort((a, b) => a.nombre.toLowerCase().compareTo(b.nombre.toLowerCase()));
    return lista;
  }

  void buscar(String texto) {
    _texto = texto;
    notifyListeners();
  }

  void filtrar(FiltroEstadoCliente filtro) {
    _filtro = filtro;
    notifyListeners();
  }

  /// Consulta el rol al iniciar sesión. Si falla queda sin saberse y la sección
  /// no se ofrece; el servidor rechaza de todos modos lo que no corresponda.
  Future<void> detectarRol() async {
    try {
      _esAdministrador = await _repositorio.esAdministrador();
    } on Object {
      _esAdministrador = null;
    }
    notifyListeners();
  }

  Future<void> cargar({bool forzar = false}) async {
    if (_estado == EstadoClientes.cargando) return;
    if (_estado == EstadoClientes.listo && !forzar) return;
    _estado = EstadoClientes.cargando;
    _error = null;
    notifyListeners();
    try {
      _clientes = await _repositorio.listar();
      _estado = EstadoClientes.listo;
    } on AppException catch (e) {
      _estado = EstadoClientes.error;
      _error = e.message;
    } on Object {
      _estado = EstadoClientes.error;
      _error = 'No pudimos cargar los clientes.';
    }
    notifyListeners();
  }

  /// Activa o desactiva la cuenta de [cliente]. Devuelve null si salió bien o el
  /// mensaje de error. Al terminar recarga la lista: desactivar también
  /// desactiva las cuentas bancarias del cliente y eso debe verse.
  Future<String?> cambiarEstado(ClienteAdmin cliente, {required bool activo}) async {
    if (_cambiandoId != null) return null;
    _cambiandoId = cliente.id;
    notifyListeners();
    String? error;
    try {
      await _repositorio.cambiarEstado(rfc: cliente.perfil.rfc, activo: activo);
    } on AppException catch (e) {
      error = e.message;
    } on Object {
      error = 'No pudimos cambiar el estado de la cuenta.';
    }
    if (error == null) {
      try {
        _clientes = await _repositorio.listar();
      } on Object {
        // El cambio ya se hizo, pero la lista mostraría el estado anterior: se pide recargarla.
        _estado = EstadoClientes.error;
        _error = 'El cambio se guardó, pero no pudimos actualizar la lista.';
      }
    }
    _cambiandoId = null;
    notifyListeners();
    return error;
  }

  void limpiar() {
    _estado = EstadoClientes.inicial;
    _clientes = const [];
    _error = null;
    _esAdministrador = null;
    _texto = '';
    _filtro = FiltroEstadoCliente.todos;
    _cambiandoId = null;
    notifyListeners();
  }
}
