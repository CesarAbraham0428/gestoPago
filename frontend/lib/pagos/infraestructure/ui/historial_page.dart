import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/breakpoints.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/historial_controller.dart';
import '../../domain/entities/compra_historial.dart';
import 'recibo.dart';

/// Compras anteriores del usuario (solo operaciones aprobadas). En ancho amplio
/// muestra el recibo junto a la lista; en angosto lo abre como pantalla.
class HistorialPage extends StatefulWidget {
  const HistorialPage({
    super.key,
    required this.controller,
    required this.referenciaSeleccionada,
    required this.alSeleccionar,
  });

  final HistorialController controller;
  final String? referenciaSeleccionada;
  final ValueChanged<String> alSeleccionar;

  @override
  State<HistorialPage> createState() => _HistorialPageState();
}

class _HistorialPageState extends State<HistorialPage> {
  @override
  void initState() {
    super.initState();
    if (widget.controller.estado == EstadoHistorial.inicial) {
      widget.controller.recargar();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ancho = Breakpoints.esAncho(context);
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final seleccion = widget.referenciaSeleccionada == null
            ? null
            : widget.controller.porReferencia(widget.referenciaSeleccionada!);

        if (!ancho && widget.referenciaSeleccionada != null) {
          return _DetalleCompra(compra: seleccion, controller: widget.controller);
        }
        final lista = _Lista(
          controller: widget.controller,
          seleccionada: widget.referenciaSeleccionada,
          alSeleccionar: widget.alSeleccionar,
        );
        if (!ancho) return lista;
        return Row(
          children: [
            SizedBox(width: 420, child: lista),
            const VerticalDivider(width: 1),
            Expanded(
              child: seleccion == null
                  ? const VistaMensaje(
                      icono: Icons.receipt_long_outlined,
                      titulo: 'Elige una compra',
                      detalle: 'Aquí verás su recibo completo.',
                    )
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(32),
                      child: Align(
                        alignment: Alignment.topLeft,
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 480),
                          child: Recibo(datos: DatosRecibo.deHistorial(seleccion)),
                        ),
                      ),
                    ),
            ),
          ],
        );
      },
    );
  }
}

class _DetalleCompra extends StatelessWidget {
  const _DetalleCompra({required this.compra, required this.controller});
  final CompraHistorial? compra;
  final HistorialController controller;

  @override
  Widget build(BuildContext context) {
    if (compra == null) {
      return controller.estado == EstadoHistorial.cargando ||
              controller.estado == EstadoHistorial.inicial
          ? const VistaCargando()
          : const VistaMensaje(
              icono: Icons.search_off,
              titulo: 'No encontramos esta compra',
              detalle: 'Vuelve a la lista para ver tus compras más recientes.',
            );
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Recibo(datos: DatosRecibo.deHistorial(compra!)),
        ),
      ),
    );
  }
}

class _Lista extends StatelessWidget {
  const _Lista({
    required this.controller,
    required this.seleccionada,
    required this.alSeleccionar,
  });

  final HistorialController controller;
  final String? seleccionada;
  final ValueChanged<String> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    switch (controller.estado) {
      case EstadoHistorial.inicial:
      case EstadoHistorial.cargando:
        return const VistaCargando(mensaje: 'Cargando tus compras');
      case EstadoHistorial.error:
        return VistaMensaje(
          icono: Icons.cloud_off_outlined,
          titulo: 'No pudimos cargar tus compras',
          detalle: controller.error ?? 'Revisa tu conexión e inténtalo de nuevo.',
          accion: controller.recargar,
        );
      case EstadoHistorial.listo:
        break;
    }
    final compras = controller.compras;
    if (compras.isEmpty) {
      return const VistaMensaje(
        icono: Icons.receipt_long_outlined,
        titulo: 'Aún no tienes compras',
        detalle: 'Cuando el servicio confirme una compra, aparecerá aquí con su recibo.',
      );
    }
    return RefreshIndicator(
      onRefresh: controller.recargar,
      child: ListView.builder(
        itemCount: compras.length + (controller.hayMas ? 1 : 0),
        itemBuilder: (context, i) {
          if (i == compras.length) return _PieLista(controller: controller);
          final compra = compras[i];
          return _FilaCompra(
            compra: compra,
            seleccionada: compra.upc == seleccionada,
            alTocar: () => alSeleccionar(compra.upc),
          );
        },
      ),
    );
  }
}

class _PieLista extends StatelessWidget {
  const _PieLista({required this.controller});
  final HistorialController controller;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          if (controller.errorMas != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                controller.errorMas!,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.rechazo),
              ),
            ),
          OutlinedButton(
            onPressed: controller.cargandoMas ? null : controller.cargarMas,
            child: controller.cargandoMas
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Ver más compras'),
          ),
        ],
      ),
    );
  }
}

class _FilaCompra extends StatelessWidget {
  const _FilaCompra({
    required this.compra,
    required this.seleccionada,
    required this.alTocar,
  });

  final CompraHistorial compra;
  final bool seleccionada;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      button: true,
      selected: seleccionada,
      child: InkWell(
        onTap: alTocar,
        child: Container(
          decoration: BoxDecoration(
            color: seleccionada ? AppColors.marcaTenue : AppColors.superficie,
            border: const Border(bottom: BorderSide(color: AppColors.lineaSuave)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Monograma(nombre: compra.servicio ?? compra.titulo, tamano: 40),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      compra.titulo,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: tema.bodyLarge?.copyWith(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 2),
                    Text(formatoFechaHora(compra.fecha), style: tema.bodySmall),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Text(
                formatoMoneda(compra.total),
                style: tema.titleMedium?.copyWith(fontFeatures: cifrasTabulares),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
