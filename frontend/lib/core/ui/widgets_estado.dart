import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_theme.dart';
import 'ilustracion.dart';

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
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: tono.texto, fontWeight: FontWeight.w600),
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
    this.color = AppColors.turquesa,
    this.fondo = AppColors.turquesaTenue,
  });

  final Color color;
  final Color fondo;
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
              Ilustracion(icono: icono, color: color, fondo: fondo),
              const SizedBox(height: 20),
              Text(
                titulo,
                style: tema.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 6),
              Text(
                detalle,
                style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
                textAlign: TextAlign.center,
              ),
              if (accion != null) ...[
                const SizedBox(height: 20),
                FilledButton.tonalIcon(
                  onPressed: accion,
                  icon: const Icon(Icons.refresh, size: 18),
                  label: Text(etiquetaAccion ?? 'Reintentar'),
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
    this.icono,
  });

  final String etiqueta;
  final String valor;
  final bool destacado;

  /// Icono opcional antes de la etiqueta.
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (icono != null) ...[
            Icon(icono, size: 18, color: AppColors.marca),
            const SizedBox(width: 10),
          ],
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
          borderRadius: BorderRadius.circular(14),
          // Con título es un resultado destacado; sin él, un aviso en línea compacto.
          border: titulo == null
              ? null
              : Border.all(color: tono.texto.withValues(alpha: 0.22)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (titulo == null)
                  Icon(icono, color: tono.texto, size: 20)
                else
                  Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.7),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(icono, color: tono.texto, size: 20),
                  ),
                SizedBox(width: titulo == null ? 10 : 12),
                Expanded(
                  child: titulo == null
                      ? Text(
                          texto,
                          style: tema.bodyMedium?.copyWith(color: tono.texto),
                        )
                      : Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: Text(
                            titulo!,
                            style: tema.titleMedium?.copyWith(
                              color: tono.texto,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                ),
              ],
            ),
            if (titulo != null) ...[
              const SizedBox(height: 8),
              Text(texto, style: tema.bodyMedium),
            ],
            if (accion != null) ...[const SizedBox(height: 14), accion!],
          ],
        ),
      ),
    );
  }
}

/// Aviso destacado para una cuenta inactiva (el servidor rechaza el acceso con 403): dice
/// que está bloqueada y qué hacer, con más presencia que un aviso en línea.
class AvisoCuentaBloqueada extends StatelessWidget {
  const AvisoCuentaBloqueada({super.key});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      liveRegion: true,
      label: 'Cuenta bloqueada. Comunícate con soporte para reactivarla.',
      excludeSemantics: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppColors.rechazoFondo, Color(0xFFFDF1EF)],
          ),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.rechazo.withValues(alpha: 0.25)),
        ),
        child: Column(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.rechazo.withValues(alpha: 0.3),
                  width: 2,
                ),
              ),
              child: const Icon(
                Icons.lock_outline,
                color: AppColors.rechazo,
                size: 26,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Cuenta bloqueada',
              style: tema.titleMedium?.copyWith(
                color: AppColors.rechazo,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Tu cuenta está inactiva y no puede iniciar sesión.',
              style: tema.bodyMedium?.copyWith(color: AppColors.tinta),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.85),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.support_agent_outlined,
                    size: 18,
                    color: AppColors.rechazo,
                  ),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      'Comunícate con soporte para reactivarla',
                      style: tema.bodySmall?.copyWith(
                        color: AppColors.rechazo,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
