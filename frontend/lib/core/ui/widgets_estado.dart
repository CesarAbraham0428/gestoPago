import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_theme.dart';

/// Etiqueta de estado. El color nunca va solo: siempre lleva texto.
class EtiquetaEstado extends StatelessWidget {
  const EtiquetaEstado({
    super.key,
    required this.texto,
    required this.tono,
    this.icono,
  });

  final String texto;
  final Tono tono;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: tono.fondo,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icono != null) ...[
            Icon(icono, size: 14, color: tono.texto),
            const SizedBox(width: 4),
          ],
          Text(
            texto,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: tono.texto,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class VistaCargando extends StatelessWidget {
  const VistaCargando({super.key, this.mensaje});
  final String? mensaje;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(strokeWidth: 3),
          ),
          if (mensaje != null) ...[
            const SizedBox(height: 16),
            Text(mensaje!, style: Theme.of(context).textTheme.bodySmall),
          ],
        ],
      ),
    );
  }
}

/// Estado vacío o de error: dice qué pasó y qué hacer.
class VistaMensaje extends StatelessWidget {
  const VistaMensaje({
    super.key,
    required this.titulo,
    required this.detalle,
    this.icono = Icons.info_outline,
    this.accion,
    this.etiquetaAccion,
  });

  final String titulo;
  final String detalle;
  final IconData icono;
  final VoidCallback? accion;
  final String? etiquetaAccion;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 380),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icono, size: 36, color: AppColors.tintaSuave),
              const SizedBox(height: 16),
              Text(titulo, style: tema.titleMedium, textAlign: TextAlign.center),
              const SizedBox(height: 6),
              Text(
                detalle,
                style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
                textAlign: TextAlign.center,
              ),
              if (accion != null) ...[
                const SizedBox(height: 20),
                OutlinedButton(
                  onPressed: accion,
                  child: Text(etiquetaAccion ?? 'Reintentar'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Fila "etiqueta / valor" para resúmenes y comprobantes.
class FilaDato extends StatelessWidget {
  const FilaDato({
    super.key,
    required this.etiqueta,
    required this.valor,
    this.destacado = false,
  });

  final String etiqueta;
  final String valor;
  final bool destacado;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 4,
            child: Text(
              etiqueta,
              style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 6,
            child: SelectableText(
              valor,
              textAlign: TextAlign.right,
              style: (destacado ? tema.titleMedium : tema.bodyMedium)?.copyWith(
                fontFeatures: cifrasTabulares,
                fontWeight: destacado ? FontWeight.w700 : FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Aviso en línea con un tono de estado: errores, avisos y resultados dentro
/// de un formulario o pantalla. Se anuncia a los lectores de pantalla.
class AvisoEstado extends StatelessWidget {
  const AvisoEstado({
    super.key,
    required this.tono,
    required this.icono,
    required this.texto,
    this.titulo,
    this.accion,
  });

  final Tono tono;
  final IconData icono;
  final String texto;
  final String? titulo;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: tono.fondo,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(icono, color: tono.texto, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: titulo == null
                      ? Text(texto, style: tema.bodyMedium?.copyWith(color: tono.texto))
                      : Text(titulo!, style: tema.titleMedium?.copyWith(color: tono.texto)),
                ),
              ],
            ),
            if (titulo != null) ...[
              const SizedBox(height: 6),
              Text(texto, style: tema.bodyMedium),
            ],
            if (accion != null) ...[const SizedBox(height: 12), accion!],
          ],
        ),
      ),
    );
  }
}
