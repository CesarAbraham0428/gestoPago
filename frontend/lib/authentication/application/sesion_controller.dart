import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/error/app_exception.dart';
import '../domain/entities/desafio_verificacion.dart';
import '../domain/entities/sesion.dart';
import '../domain/ports/almacen_sesion.dart';
import '../domain/ports/autenticacion_gateway.dart';

/// Administra la sesión activa: acceso en dos pasos (contraseña y código),
/// restauración, expiración automática y cierre. Es el único lugar que conoce
/// el token vigente.
///
/// El servidor guarda una sola sesión por usuario: cerrar sesión la invalida allí
/// además de borrar el token de este dispositivo.
class SesionController extends ChangeNotifier {
  SesionController({
    required this._gateway,
    required this._almacen,
    this.intervaloVerificacion = const Duration(seconds: 10),
  });

  final AutenticacionGateway _gateway;
  final AlmacenSesion _almacen;

  /// Cada cuánto se pregunta al servidor si la sesión sigue vigente, para que un
  /// cierre desde otro dispositivo saque al usuario aunque no esté navegando.
  final Duration intervaloVerificacion;

  Sesion? _sesion;
  Timer? _temporizador;
  Timer? _sondeo;
  bool _verificando = false;
  bool _restaurada = false;
  bool _cerrando = false;
  String? _aviso;

  Sesion? get sesion => _sesion;
  String? get token => _sesion?.token;
  bool get autenticado => _sesion != null;

  /// Falso hasta que termina de leerse el almacén al arrancar.
  bool get restaurada => _restaurada;

  /// Mensaje para mostrar en el login tras una expiración o cierre forzado.
  String? get aviso => _aviso;

  Future<void> restaurar() async {
    try {
      final guardada = await _almacen.leer();
      if (guardada != null && guardada.estaVigente) {
        _activar(guardada);
      } else if (guardada != null) {
        await _almacen.borrar();
        _aviso = 'Tu sesión expiró. Inicia sesión de nuevo.';
      }
    } on Object {
      _sesion = null;
    }
    _restaurada = true;
    notifyListeners();
  }

  /// Paso 1: valida usuario y contraseña; el servidor abre el desafío del segundo factor.
  /// Lanza una `AppException` con un mensaje apto para el usuario si falla.
  Future<DesafioVerificacion> solicitarCodigo({
    required String correo,
    required String password,
  }) {
    return _gateway.iniciarSesion(
      correo: correo.trim().toLowerCase(),
      password: password,
    );
  }

  /// Paso 2: valida el código y abre la sesión. Lanza `SesionActivaException` si
  /// hay otra sesión abierta; repetir con [cerrarSesionAnterior] la termina.
  Future<void> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    bool cerrarSesionAnterior = false,
  }) async {
    final nueva = await _gateway.verificarCodigo(
      desafio: desafio,
      codigo: codigo.trim(),
      cerrarSesionAnterior: cerrarSesionAnterior,
    );
    try {
      await _almacen.guardar(nueva);
    } on Object {
      // Sin almacenamiento seguro la sesión vive solo en memoria.
    }
    _aviso = null;
    _activar(nueva);
    notifyListeners();
  }

  /// Cierra la sesión también en el servidor; si no responde, igual se cierra aquí.
  Future<void> cerrarSesion({String? aviso}) async {
    if (_sesion == null || _cerrando) return;
    _cerrando = true;
    try {
      await _gateway.cerrarSesion();
    } on Object {
      // El token vence solo; no se deja al usuario atrapado por un fallo de red.
    }
    _cerrando = false;
    await _cerrar(aviso);
  }

  /// Lo invoca el cliente HTTP cuando una ruta protegida responde 401: la sesión
  /// ya no es válida en el servidor (vencida, cerrada o reemplazada).
  void sesionInvalidada(String mensaje) {
    if (_sesion == null || _cerrando) return;
    unawaited(_cerrar(mensaje));
  }

  /// Pregunta al servidor si la sesión sigue activa; si respondió 401 la cierra
  /// aquí. Un fallo de red no cierra nada: se reintenta en el siguiente sondeo.
  Future<void> verificarSesion() async {
    if (_sesion == null || _cerrando || _verificando) return;
    _verificando = true;
    try {
      await _gateway.verificarSesion();
    } on ApiException catch (e) {
      if (e.statusCode == 401) sesionInvalidada(e.message);
    } on Object {
      // Sin conexión no se sabe; se vuelve a intentar.
    } finally {
      _verificando = false;
    }
  }

  void _activar(Sesion sesion) {
    _sesion = sesion;
    _sondeo?.cancel();
    _sondeo = Timer.periodic(
      intervaloVerificacion,
      (_) => unawaited(verificarSesion()),
    );
    _temporizador?.cancel();
    _temporizador = Timer(sesion.tiempoRestante, () {
      unawaited(
        _cerrar('Tu sesión expiró por seguridad. Inicia sesión de nuevo.'),
      );
    });
  }

  Future<void> _cerrar(String? aviso) async {
    _temporizador?.cancel();
    _sondeo?.cancel();
    _sesion = null;
    _aviso = aviso;
    notifyListeners();
    try {
      await _almacen.borrar();
    } on Object {
      // Ya no hay sesión en memoria; el borrado se reintenta en el próximo cierre.
    }
  }

  @override
  void dispose() {
    _temporizador?.cancel();
    _sondeo?.cancel();
    super.dispose();
  }
}
