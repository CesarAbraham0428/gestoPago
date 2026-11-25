import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/perfil.dart';
import '../domain/ports/perfil_repository.dart';

enum EstadoPerfil { inicial, cargando, listo, error }

/// Casos de uso "ver mi perfil" y "editar mi perfil".
class PerfilController extends ChangeNotifier {
  PerfilController(this._repositorio);

  final PerfilRepository _repositorio;

  EstadoPerfil _estado = EstadoPerfil.inicial;
  Perfil? _perfil;
  String? _error;
  bool _guardando = false;

  EstadoPerfil get estado => _estado;
  Perfil? get perfil => _perfil;
  String? get error => _error;
  bool get guardando => _guardando;

  Future<void> cargar() async {
    if (_estado == EstadoPerfil.cargando) return;
    _estado = EstadoPerfil.cargando;
    _error = null;
    notifyListeners();
    try {
      _perfil = await _repositorio.obtener();
      _estado = EstadoPerfil.listo;
    } on AppException catch (e) {
      _estado = EstadoPerfil.error;
      _error = e.message;
    } on Object {
      _estado = EstadoPerfil.error;
      _error = 'No pudimos cargar tu perfil.';
    }
    notifyListeners();
  }

  /// Guarda solo lo que cambió respecto a [editable] del perfil actual.
  /// Devuelve null si salió bien (o no había cambios) o el mensaje de error.
  Future<String?> guardar(Map<String, Object?> editado) async {
    final actual = _perfil;
    if (actual == null || _guardando) return null;
    final cambios = cambiosEntre(actual.aMapaEditable(), editado);
    if (cambios.isEmpty) return null;
    _guardando = true;
    notifyListeners();
    String? error;
    try {
      _perfil = await _repositorio.actualizar(
        rfc: actual.rfc,
        cambios: cambios,
      );
    } on AppException catch (e) {
      error = e.message;
    } on Object {
      error = 'No pudimos guardar tus cambios.';
    }
    _guardando = false;
    notifyListeners();
    return error;
  }

  void limpiar() {
    _estado = EstadoPerfil.inicial;
    _perfil = null;
    _error = null;
    _guardando = false;
    notifyListeners();
  }
}
