import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../application/sesion_controller.dart';

/// Cierra la sesión tras un rato sin interacción (toques, desplazamiento o
/// teclado). Protege un equipo desatendido con la sesión abierta. Al volver a
/// la app (o a la pestaña) verifica de inmediato que la sesión siga activa.
class GuardiaInactividad extends StatefulWidget {
  const GuardiaInactividad({
    super.key,
    required this.sesion,
    required this.limite,
    required this.child,
  });

  final SesionController sesion;
  final Duration limite;
  final Widget child;

  @override
  State<GuardiaInactividad> createState() => _GuardiaInactividadState();
}

class _GuardiaInactividadState extends State<GuardiaInactividad> {
  Timer? _temporizador;
  late final AppLifecycleListener _ciclo;

  @override
  void initState() {
    super.initState();
    _ciclo = AppLifecycleListener(onResume: widget.sesion.verificarSesion);
    widget.sesion.addListener(_reiniciar);
    HardwareKeyboard.instance.addHandler(_alTeclear);
    _reiniciar();
  }

  @override
  void dispose() {
    _ciclo.dispose();
    widget.sesion.removeListener(_reiniciar);
    HardwareKeyboard.instance.removeHandler(_alTeclear);
    _temporizador?.cancel();
    super.dispose();
  }

  bool _alTeclear(KeyEvent _) {
    _reiniciar();
    return false; // no consume la tecla
  }

  /// Sin sesión no hay nada que vigilar; con sesión, cada interacción reinicia la cuenta.
  void _reiniciar() {
    _temporizador?.cancel();
    if (!widget.sesion.autenticado) return;
    _temporizador = Timer(widget.limite, () {
      widget.sesion.cerrarSesion(
        aviso: 'Cerramos tu sesión por inactividad. Inicia sesión de nuevo.',
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _reiniciar(),
      onPointerMove: (_) => _reiniciar(),
      onPointerSignal: (_) => _reiniciar(),
      child: widget.child,
    );
  }
}
