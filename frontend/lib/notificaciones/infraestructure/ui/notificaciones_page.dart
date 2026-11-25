import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/notificaciones_controller.dart';
import '../../domain/entities/notificacion.dart';

/// Bandeja de notificaciones: resumen con el total sin leer, filtro, avisos
/// agrupados por día y un recordatorio de seguridad. Un pago que el usuario no
/// reconoce debe poder verse aquí aunque no estuviera conectado cuando ocurrió.
class NotificacionesPage extends StatefulWidget {
  const NotificacionesPage({
    super.key,
    required this.controller,
    required this.alVerCompra,
  });

  final NotificacionesController controller;
  final ValueChanged<String> alVerCompra;

  @override
  State<NotificacionesPage> createState() => _NotificacionesPageState();
}

class _NotificacionesPageState extends State<NotificacionesPage> {
  @override
  void initState() {
    super.initState();
    // Siempre se refresca al entrar. Tras el primer cuadro: notificar durante el
    // build rompería a los demás oyentes (la insignia del menú).
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => widget.controller.recargar(silencioso: true),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final c = widget.controller;
        switch (c.estado) {
          case EstadoNotificaciones.inicial:
          case EstadoNotificaciones.cargando:
            return const VistaCargando(mensaje: 'Buscando tus notificaciones…');
          case EstadoNotificaciones.error:
            return VistaMensaje(
              icono: Icons.cloud_off_outlined,
              color: AppColors.indigo,
              fondo: AppColors.indigoTenue,
              titulo: 'No pudimos cargar tus notificaciones',
              detalle: c.error ?? 'Revisa tu conexión e inténtalo de nuevo.',
              accion: c.recargar,
            );
          case EstadoNotificaciones.listo:
            return _Contenido(controller: c, alVerCompra: widget.alVerCompra);
        }
      },
    );
  }
}

class _Contenido extends StatelessWidget {
  const _Contenido({required this.controller, required this.alVerCompra});
  final NotificacionesController controller;
  final ValueChanged<String> alVerCompra;

  @override
  Widget build(BuildContext context) {
    final c = controller;
    final visibles = c.visibles;
    final filas = <Widget>[
      _Resumen(controller: c),
      const SizedBox(height: 16),
      _Filtro(controller: c),
      const SizedBox(height: 8),
    ];

    if (c.items.isEmpty) {
      filas.add(
        const Padding(
          padding: EdgeInsets.only(top: 24),
          child: VistaMensaje(
            icono: Icons.notifications_none_outlined,
            color: AppColors.indigo,
            fondo: AppColors.indigoTenue,
            titulo: 'Todo en calma',
            detalle:
                'Cuando un pago se apruebe, se rechace o quede en revisión, '
                'te avisaremos aquí.',
          ),
        ),
      );
    } else if (visibles.isEmpty) {
      filas.add(
        const Padding(
          padding: EdgeInsets.only(top: 24),
          child: VistaMensaje(
            icono: Icons.done_all,
            color: AppColors.exito,
            fondo: AppColors.exitoFondo,
            titulo: 'Estás al día',
            detalle: 'No tienes notificaciones sin leer.',
          ),
        ),
      );
    } else {
      String? diaActual;
      for (final n in visibles) {
        final dia = _etiquetaDia(n.fecha);
        if (dia != diaActual) {
          diaActual = dia;
          filas.add(_EncabezadoDia(texto: dia));
        }
        filas.add(
          _Tarjeta(
            key: ValueKey(n.id),
            notificacion: n,
            alTocar: () => controller.marcarLeida(n),
            alVerCompra: n.upc == null ? null : () => alVerCompra(n.upc!),
          ),
        );
      }
      if (c.hayMas || c.errorMas != null) {
        filas.add(_CargarMas(controller: c));
      }
    }

    return RefreshIndicator(
      onRefresh: () => controller.recargar(silencioso: true),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          Align(
            alignment: Alignment.topCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 720),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: filas,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Cabecera con degradado: cuántos avisos faltan por leer y el atajo para limpiarlos.
class _Resumen extends StatelessWidget {
  const _Resumen({required this.controller});
  final NotificacionesController controller;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final n = controller.noLeidas;
    final titulo = n == 0
        ? 'Estás al día'
        : n == 1
        ? 'Tienes 1 aviso sin leer'
        : 'Tienes $n avisos sin leer';
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.indigo, Color(0xFF3540A8)],
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: AppColors.indigo.withValues(alpha: 0.25),
            blurRadius: 24,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Wrap(
        spacing: 16,
        runSpacing: 14,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.18),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.notifications_active_outlined,
                      color: Colors.white,
                      size: 28,
                    ),
                  ),
                  if (n > 0)
                    Positioned(
                      right: -2,
                      top: -2,
                      child: Container(
                        width: 14,
                        height: 14,
                        decoration: BoxDecoration(
                          color: AppColors.coral,
                          shape: BoxShape.circle,
                          border: Border.all(color: AppColors.indigo, width: 2),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 14),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 380),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      titulo,
                      style: tema.titleLarge?.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Te avisamos de cada pago aprobado, rechazado o en revisión.',
                      style: tema.bodyMedium?.copyWith(
                        color: Colors.white.withValues(alpha: 0.85),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              disabledForegroundColor: Colors.white38,
              side: BorderSide(color: n > 0 ? Colors.white70 : Colors.white24),
            ),
            onPressed: n == 0 ? null : controller.marcarTodasLeidas,
            icon: const Icon(Icons.done_all, size: 18),
            label: const Text('Marcar todo como leído'),
          ),
        ],
      ),
    );
  }
}

class _Filtro extends StatelessWidget {
  const _Filtro({required this.controller});
  final NotificacionesController controller;

  @override
  Widget build(BuildContext context) {
    final sinLeer = controller.noLeidas;
    return Wrap(
      spacing: 8,
      children: [
        ChoiceChip(
          label: const Text('Todas'),
          selected: !controller.soloNoLeidas,
          onSelected: (_) => controller.filtrarNoLeidas(false),
        ),
        ChoiceChip(
          label: Text(sinLeer == 0 ? 'Sin leer' : 'Sin leer ($sinLeer)'),
          selected: controller.soloNoLeidas,
          onSelected: (_) => controller.filtrarNoLeidas(true),
        ),
      ],
    );
  }
}

class _EncabezadoDia extends StatelessWidget {
  const _EncabezadoDia({required this.texto});
  final String texto;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 16, 4, 8),
      child: Text(
        texto.toUpperCase(),
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: AppColors.tintaSuave,
          letterSpacing: 1.2,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

/// Un aviso. Sin leer: fondo y borde con el tono del evento y un punto; al tocarlo
/// se marca como leído. Aparece con un leve desvanecimiento.
class _Tarjeta extends StatelessWidget {
  const _Tarjeta({
    super.key,
    required this.notificacion,
    required this.alTocar,
    required this.alVerCompra,
  });

  final Notificacion notificacion;
  final VoidCallback alTocar;
  final VoidCallback? alVerCompra;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final n = notificacion;
    final visual = _visual(n.tipo);
    final tono = visual.tono;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      builder: (context, t, hijo) => Opacity(
        opacity: t,
        child: Transform.translate(offset: Offset(0, (1 - t) * 8), child: hijo),
      ),
      child: Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Semantics(
          button: !n.leida,
          label: '${n.leida ? '' : 'Sin leer. '}${n.titulo}. ${n.mensaje}',
          excludeSemantics: true,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 250),
            decoration: BoxDecoration(
              color: n.leida
                  ? AppColors.superficie
                  : tono.fondo.withValues(alpha: 0.55),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: n.leida
                    ? AppColors.lineaSuave
                    : tono.texto.withValues(alpha: 0.28),
              ),
            ),
            child: Material(
              color: Colors.transparent,
              borderRadius: BorderRadius.circular(16),
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: n.leida ? null : alTocar,
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: n.leida ? tono.fondo : Colors.white,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: tono.texto.withValues(alpha: 0.25),
                          ),
                        ),
                        child: Icon(visual.icono, color: tono.texto, size: 24),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    n.titulo,
                                    style: tema.titleSmall?.copyWith(
                                      fontWeight: n.leida
                                          ? FontWeight.w600
                                          : FontWeight.w800,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  _tiempo(n.fecha),
                                  style: tema.bodySmall?.copyWith(
                                    color: AppColors.tintaSuave,
                                  ),
                                ),
                                if (!n.leida) ...[
                                  const SizedBox(width: 8),
                                  Container(
                                    width: 9,
                                    height: 9,
                                    decoration: BoxDecoration(
                                      color: tono.texto,
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                ],
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              n.mensaje,
                              style: tema.bodyMedium?.copyWith(
                                color: AppColors.tintaSuave,
                                height: 1.35,
                              ),
                            ),
                            if (alVerCompra != null) ...[
                              const SizedBox(height: 8),
                              TextButton.icon(
                                style: TextButton.styleFrom(
                                  foregroundColor: tono.texto,
                                  padding: EdgeInsets.zero,
                                  minimumSize: const Size(0, 32),
                                  tapTargetSize:
                                      MaterialTapTargetSize.shrinkWrap,
                                ),
                                onPressed: () {
                                  alTocar();
                                  alVerCompra!();
                                },
                                icon: const Icon(
                                  Icons.receipt_long_outlined,
                                  size: 18,
                                ),
                                label: const Text('Ver compra'),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CargarMas extends StatelessWidget {
  const _CargarMas({required this.controller});
  final NotificacionesController controller;

  @override
  Widget build(BuildContext context) {
    final c = controller;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Column(
        children: [
          if (c.errorMas != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                c.errorMas!,
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: AppColors.rechazo),
              ),
            ),
          c.cargandoMas
              ? const Padding(
                  padding: EdgeInsets.all(8),
                  child: SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 3),
                  ),
                )
              : OutlinedButton.icon(
                  onPressed: c.cargarMas,
                  icon: const Icon(Icons.expand_more),
                  label: const Text('Ver más antiguas'),
                ),
        ],
      ),
    );
  }
}

({IconData icono, Tono tono}) _visual(TipoNotificacion tipo) => switch (tipo) {
  TipoNotificacion.pagoAprobado => (
    icono: Icons.check_circle,
    tono: Tono.exito,
  ),
  TipoNotificacion.pagoRechazado => (icono: Icons.cancel, tono: Tono.rechazo),
  TipoNotificacion.pagoEnRevision => (
    icono: Icons.hourglass_top,
    tono: Tono.pendiente,
  ),
  TipoNotificacion.otro => (icono: Icons.notifications, tono: Tono.aviso),
};

DateTime _soloDia(DateTime f) => DateTime(f.year, f.month, f.day);

String _etiquetaDia(DateTime fecha) {
  final local = fecha.toLocal();
  final dias = _soloDia(DateTime.now()).difference(_soloDia(local)).inDays;
  if (dias <= 0) return 'Hoy';
  if (dias == 1) return 'Ayer';
  return formatoFecha(local);
}

/// "Justo ahora", "hace 12 min" durante la primera hora; después, la hora del día.
String _tiempo(DateTime fecha) {
  final minutos = DateTime.now().difference(fecha).inMinutes;
  if (minutos < 1) return 'Justo ahora';
  if (minutos < 60) return 'hace $minutos min';
  return formatoHora(fecha);
}
