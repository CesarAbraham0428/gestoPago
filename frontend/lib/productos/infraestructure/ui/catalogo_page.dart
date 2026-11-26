import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/breakpoints.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/catalogo_controller.dart';
import '../../domain/entities/producto.dart';
import 'detalle_producto.dart';

/// Catálogo de productos: buscador y filtros arriba y una cuadrícula de cards
/// que se adapta al ancho. Al elegir una card se abre el detalle del producto.
class CatalogoPage extends StatefulWidget {
  const CatalogoPage({
    super.key,
    required this.controller,
    required this.claveSeleccionada,
    required this.alSeleccionar,
    required this.alVolver,
    required this.construirAccionCompra,
  });

  final CatalogoController controller;
  final String? claveSeleccionada;
  final ValueChanged<String> alSeleccionar;
  final VoidCallback alVolver;

  /// Acción de compra del producto, aportada por el módulo de pagos.
  final Widget Function(BuildContext context, Producto producto) construirAccionCompra;

  @override
  State<CatalogoPage> createState() => _CatalogoPageState();
}

class _CatalogoPageState extends State<CatalogoPage> {
  final _busqueda = TextEditingController();

  @override
  void initState() {
    super.initState();
    widget.controller.cargar();
  }

  @override
  void dispose() {
    _busqueda.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final clave = widget.claveSeleccionada;
        if (clave != null) {
          return _Detalle(
            controller: widget.controller,
            producto: widget.controller.porClave(clave),
            alVolver: widget.alVolver,
            construirAccionCompra: widget.construirAccionCompra,
          );
        }
        return _PanelLista(
          controller: widget.controller,
          busqueda: _busqueda,
          alSeleccionar: widget.alSeleccionar,
        );
      },
    );
  }
}

class _Detalle extends StatelessWidget {
  const _Detalle({
    required this.controller,
    required this.producto,
    required this.alVolver,
    required this.construirAccionCompra,
  });

  final CatalogoController controller;
  final Producto? producto;
  final VoidCallback alVolver;
  final Widget Function(BuildContext, Producto) construirAccionCompra;

  @override
  Widget build(BuildContext context) {
    final p = producto;
    if (p == null) {
      return controller.estado == EstadoCatalogo.cargando ||
              controller.estado == EstadoCatalogo.inicial
          ? const VistaCargando()
          : const VistaMensaje(
              icono: Icons.search_off,
              titulo: 'No encontramos este producto',
              detalle: 'Puede que ya no esté en el catálogo. Vuelve a la lista para elegir otro.',
            );
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 640),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: alVolver,
                  icon: const Icon(Icons.arrow_back, size: 18),
                  label: const Text('Volver al catálogo'),
                ),
              ),
              const SizedBox(height: 8),
              DetalleProducto(
                key: ValueKey(p.clave),
                producto: p,
                accionCompra: construirAccionCompra(context, p),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PanelLista extends StatelessWidget {
  const _PanelLista({
    required this.controller,
    required this.busqueda,
    required this.alSeleccionar,
  });

  final CatalogoController controller;
  final TextEditingController busqueda;
  final ValueChanged<String> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: TextField(
            controller: busqueda,
            onChanged: controller.buscar,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: 'Buscar por servicio o producto',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: busqueda.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: 'Borrar búsqueda',
                      icon: const Icon(Icons.close),
                      onPressed: () {
                        busqueda.clear();
                        controller.buscar('');
                      },
                    ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: _Filtros(controller: controller),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 8, 4),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  controller.estado == EstadoCatalogo.listo
                      ? '${controller.visibles.length} de ${controller.total} productos'
                      : '',
                  style: tema.bodySmall,
                ),
              ),
              Flexible(
                child: Text(
                  'Solo los que puedo comprar',
                  style: tema.bodySmall,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Switch(
                value: controller.soloDisponibles,
                onChanged: controller.alternarSoloDisponibles,
              ),
            ],
          ),
        ),
        const Divider(),
        Expanded(
          child: _Contenido(controller: controller, alSeleccionar: alSeleccionar),
        ),
      ],
    );
  }
}

/// Un chip por cada tipo de servicio que existe en el catálogo cargado. En
/// pantallas anchas se reparten en varias líneas; en angostas se desplazan.
class _Filtros extends StatelessWidget {
  const _Filtros({required this.controller});
  final CatalogoController controller;

  @override
  Widget build(BuildContext context) {
    final chips = [
      _FiltroTipo(
        etiqueta: 'Todo',
        activo: controller.tipo == null,
        alTocar: () => controller.filtrarTipo(null),
      ),
      for (final tipo in controller.tiposPresentes)
        _FiltroTipo(
          etiqueta: etiquetaTipoServicio(tipo),
          activo: controller.tipo == tipo,
          alTocar: () => controller.filtrarTipo(tipo),
        ),
    ];
    if (Breakpoints.esAncho(context, 600)) {
      return Align(
        alignment: Alignment.centerLeft,
        child: Wrap(runSpacing: 8, children: chips),
      );
    }
    return SizedBox(
      height: 44,
      child: ListView(scrollDirection: Axis.horizontal, children: chips),
    );
  }
}

class _FiltroTipo extends StatelessWidget {
  const _FiltroTipo({
    required this.etiqueta,
    required this.activo,
    required this.alTocar,
  });

  final String etiqueta;
  final bool activo;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(etiqueta),
        selected: activo,
        onSelected: (_) => alTocar(),
        side: BorderSide(color: activo ? AppColors.marca : AppColors.linea),
      ),
    );
  }
}

class _Contenido extends StatelessWidget {
  const _Contenido({required this.controller, required this.alSeleccionar});

  final CatalogoController controller;
  final ValueChanged<String> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    switch (controller.estado) {
      case EstadoCatalogo.inicial:
      case EstadoCatalogo.cargando:
        return const VistaCargando(mensaje: 'Cargando catálogo');
      case EstadoCatalogo.error:
        return VistaMensaje(
          icono: Icons.cloud_off_outlined,
          color: AppColors.coral,
          fondo: AppColors.coralTenue,
          titulo: 'No pudimos cargar el catálogo',
          detalle: controller.error ?? 'Revisa tu conexión e inténtalo de nuevo.',
          accion: () => controller.cargar(forzar: true),
        );
      case EstadoCatalogo.listo:
        break;
    }
    final productos = controller.visibles;
    if (productos.isEmpty) {
      return VistaMensaje(
        icono: Icons.search_off,
        titulo: 'Sin resultados',
        detalle: 'Prueba con otro nombre o quita los filtros.',
        etiquetaAccion: 'Quitar filtros',
        accion: () {
          controller.buscar('');
          controller.filtrarTipo(null);
        },
      );
    }

    // Con la letra del sistema grande la card necesita más alto.
    final alto = 216 * MediaQuery.textScalerOf(context).scale(1).clamp(1.0, 1.6);
    return RefreshIndicator(
      onRefresh: () => controller.cargar(forzar: true),
      child: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 280,
          mainAxisExtent: alto,
          mainAxisSpacing: 14,
          crossAxisSpacing: 14,
        ),
        itemCount: productos.length,
        itemBuilder: (context, i) => _TarjetaProducto(
          producto: productos[i],
          alTocar: () => alSeleccionar(productos[i].clave),
        ),
      ),
    );
  }
}

/// Card del producto: banda con el color de la marca y sus iniciales, estado
/// de disponibilidad, nombre, tipo de compra y precio.
class _TarjetaProducto extends StatelessWidget {
  const _TarjetaProducto({required this.producto, required this.alTocar});

  final Producto producto;
  final VoidCallback alTocar;

  String get _subtitulo => switch (producto.tipoCompra) {
    TipoCompra.recarga => producto.esCanjeable ? 'Código canjeable' : 'Recarga de saldo',
    TipoCompra.pagoServicio => producto.comision > 0
        ? 'Pago de servicio · comisión ${formatoMoneda(producto.comision)}'
        : 'Pago de servicio · sin comisión',
    TipoCompra.noDisponible => 'Solo consulta',
  };

  String get _precio => switch (producto.tipoCompra) {
    TipoCompra.recarga => formatoMoneda(producto.montoFijo!),
    TipoCompra.pagoServicio => 'Monto libre',
    TipoCompra.noDisponible => '—',
  };

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final disponible = producto.disponibleParaCompra;
    final color = Monograma.colorDe(producto.servicio);
    return Semantics(
      button: true,
      child: Opacity(
        opacity: disponible ? 1 : 0.7,
        child: Material(
          color: AppColors.superficie,
          elevation: 1,
          shadowColor: Colors.black26,
          borderRadius: BorderRadius.circular(18),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: alTocar,
            hoverColor: color.withValues(alpha: 0.06),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  height: 68,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [color, Color.lerp(color, Colors.black, 0.35)!],
                    ),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 42,
                        height: 42,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          Monograma.iniciales(producto.servicio),
                          style: TextStyle(
                            color: color,
                            fontWeight: FontWeight.w800,
                            fontSize: 16,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Align(
                          alignment: Alignment.centerRight,
                          child: _PildoraEstado(disponible: disponible),
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          producto.servicio,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: tema.bodySmall,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          producto.nombre,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: tema.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          _subtitulo,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: tema.bodySmall,
                        ),
                        const Spacer(),
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                _precio,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: tema.titleLarge?.copyWith(
                                  fontFeatures: cifrasTabulares,
                                  color: disponible ? AppColors.marcaOscura : AppColors.tintaSuave,
                                ),
                              ),
                            ),
                            if (disponible)
                              const Icon(Icons.arrow_forward, size: 20, color: AppColors.marca),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PildoraEstado extends StatelessWidget {
  const _PildoraEstado({required this.disponible});
  final bool disponible;

  @override
  Widget build(BuildContext context) {
    final tono = disponible ? AppColors.exito : AppColors.tintaSuave;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(disponible ? Icons.check_circle : Icons.block, size: 13, color: tono),
          const SizedBox(width: 4),
          Flexible(
            child: Text(
              disponible ? 'Disponible' : 'No disponible',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: tono),
            ),
          ),
        ],
      ),
    );
  }
}
