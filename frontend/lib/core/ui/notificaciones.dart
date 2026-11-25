import 'package:flutter/material.dart';
import 'package:toastification/toastification.dart';

/// Servicio global de notificaciones (toasts) arriba a la derecha.
/// Requiere que la app esté envuelta en [ToastificationWrapper] (ver `app.dart`).
class Notificaciones {
  const Notificaciones._();

  static void exito(String titulo, {String? detalle}) =>
      _mostrar(ToastificationType.success, titulo, detalle);

  static void error(String titulo, {String? detalle}) =>
      _mostrar(ToastificationType.error, titulo, detalle);

  static void info(String titulo, {String? detalle}) =>
      _mostrar(ToastificationType.info, titulo, detalle);

  static void _mostrar(
    ToastificationType tipo,
    String titulo,
    String? detalle,
  ) {
    toastification.show(
      type: tipo,
      style: ToastificationStyle.minimal,
      alignment: Alignment.topRight,
      autoCloseDuration: const Duration(seconds: 4),
      title: Text(titulo),
      description: detalle == null ? null : Text(detalle),
      showProgressBar: false,
      dragToClose: true,
    );
  }
}
