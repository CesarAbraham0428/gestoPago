import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../domain/entities/estado_pago.dart';

/// Cómo se ve cada estado de un pago: etiqueta, tono y colores del recibo.
class EstadoVisual {
  const EstadoVisual({
    required this.texto,
    required this.tono,
    required this.icono,
    required this.titulo,
    required this.iconoGrande,
    required this.colores,
    this.motivo,
  });

  final String texto;
  final Tono tono;
  final IconData icono;

  /// Encabezado del recibo.
  final String titulo;
  final IconData iconoGrande;
  final List<Color> colores;

  /// Explicación para el usuario cuando el pago no quedó aprobado.
  final String? motivo;
}

EstadoVisual estadoVisual(CategoriaPago categoria) => switch (categoria) {
  CategoriaPago.aprobado => const EstadoVisual(
    texto: 'Aprobado',
    tono: Tono.exito,
    icono: Icons.check_circle_outline,
    titulo: 'Compra confirmada',
    iconoGrande: Icons.verified,
    colores: [AppColors.marca, AppColors.marcaOscura],
  ),
  CategoriaPago.enProceso => const EstadoVisual(
    texto: 'Pendiente',
    tono: Tono.pendiente,
    icono: Icons.schedule,
    titulo: 'Pago pendiente',
    motivo:
        'Estamos esperando la confirmación del proveedor del servicio. '
        'Si no se completa, te devolvemos el importe a tu saldo.',
    iconoGrande: Icons.hourglass_top,
    colores: [AppColors.pendiente, Color(0xFF5C3A00)],
  ),
  CategoriaPago.rechazado => const EstadoVisual(
    texto: 'Rechazado',
    tono: Tono.rechazo,
    icono: Icons.cancel_outlined,
    titulo: 'Pago rechazado',
    iconoGrande: Icons.block,
    colores: [AppColors.rechazo, Color(0xFF7A1B13)],
    // Todo rechazo pasa por devolverSaldo en el backend: el cargo siempre se revierte.
    motivo:
        'El proveedor del servicio no pudo procesar el pago, por lo que no se '
        'realizó ningún cargo. El importe ya está de vuelta en tu saldo.',
  ),
};
