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

/// Catálogo de productos. En pantallas anchas muestra la lista y el detalle
/// lado a lado; en pantallas angostas la lista abre el detalle como pantalla.
class CatalogoPage extends StatefulWidget {
  const CatalogoPage({
    super.key,
    required this.controller,
    required this.claveSeleccionada,
    required this.alSeleccionar,
    required this.construirAccionCompra,
  });

  final CatalogoController controller;
  final String? claveSeleccionada;
  final ValueChanged<String> alSeleccionar;

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
    final ancho = Breakpoints.esAncho(context);
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final seleccionado = widget.claveSeleccionada == null
            ? null
            : widget.controller.porClave(widget.claveSeleccionada!);

        if (!ancho && widget.claveSeleccionada != null) {
          return _DetalleAngosto(
            controller: widget.controller,
            producto: seleccionado,
            construirAccionCompra: widget.construirAccionCompra,
          );
        }

        final lista = _PanelLista(
          controller: widget.controller,
          busqueda: _busqueda,
          claveSeleccionada: widget.claveSeleccionada,
          alSeleccionar: widget.alSeleccionar,
        );
        if (!ancho) return lista;

        return Row(
          children: [
            SizedBox(width: 420, child: lista),
            const VerticalDivider(width: 1),
            Expanded(
              child: seleccionado == null
                  ? const VistaMensaje(
                      icono: Icons.touch_app_outlined,
                      titulo: 'Elige un producto',
                      detalle: 'Aquí verás su precio y podrás comprarlo.',
                    )
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(32),
                      child: Align(
                        alignment: Alignment.topCenter,
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 640),
                          child: DetalleProducto(
                            key: ValueKey(seleccionado.clave),
                            producto: seleccionado,
                            accionCompra: widget.construirAccionCompra(context, seleccionado),
                          ),
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

class _DetalleAngosto extends StatelessWidget {
  const _DetalleAngosto({
    required this.controller,
    required this.producto,
    required this.construirAccionCompra,
  });

  final CatalogoController controller;
  final Producto? producto;
  final Widget Function(BuildContext, Producto) construirAccionCompra;

  @override
  Widget build(BuildContext context) {
    if (producto == null) {
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
          child: DetalleProducto(
            key: ValueKey(producto!.clave),
            producto: producto!,
            accionCompra: construirAccionCompra(context, producto!),
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
    required this.claveSeleccionada,
    required this.alSeleccionar,
  });

  final CatalogoController controller;
  final TextEditingController busqueda;
  final String? claveSeleccionada;
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
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              _FiltroCategoria(
                etiqueta: 'Todo',
                activo: controller.categoria == null,
                alTocar: () => controller.filtrarCategoria(null),
              ),
              for (final categoria in controller.categoriasPresentes)
                _FiltroCategoria(
                  etiqueta: categoria.etiqueta,
                  activo: controller.categoria == categoria,
                  alTocar: () => controller.filtrarCategoria(categoria),
                ),
            ],
          ),
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
        Expanded(child: _Contenido(
          controller: controller,
          claveSeleccionada: claveSeleccionada,
          alSeleccionar: alSeleccionar,
        )),
      ],
    );
  }
}

class _FiltroCategoria extends StatelessWidget {
  const _FiltroCategoria({
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
  const _Contenido({
    required this.controller,
    required this.claveSeleccionada,
    required this.alSeleccionar,
  });

  final CatalogoController controller;
  final String? claveSeleccionada;
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
          controller.filtrarCategoria(null);
        },
      );
    }

    // Lista plana con un encabezado por servicio; los productos ya vienen ordenados.
    final filas = <Object>[];
    String? servicioActual;
    for (final p in productos) {
      if (p.servicio != servicioActual) {
        servicioActual = p.servicio;
        filas.add(p.servicio);
      }
      filas.add(p);
    }

    return RefreshIndicator(
      onRefresh: () => controller.cargar(forzar: true),
      child: ListView.builder(
        itemCount: filas.length,
        itemBuilder: (context, i) {
          final fila = filas[i];
          if (fila is String) return _EncabezadoServicio(nombre: fila);
          final producto = fila as Producto;
          return _FilaProducto(
            producto: producto,
            seleccionado: producto.clave == claveSeleccionada,
            alTocar: () => alSeleccionar(producto.clave),
          );
        },
      ),
    );
  }
}

class _EncabezadoServicio extends StatelessWidget {
  const _EncabezadoServicio({required this.nombre});
  final String nombre;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.papel,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Row(
        children: [
          Monograma(nombre: nombre, tamano: 28),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              nombre,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
        ],
      ),
    );
  }
}

class _FilaProducto extends StatelessWidget {
  const _FilaProducto({
    required this.producto,
    required this.seleccionado,
    required this.alTocar,
  });

  final Producto producto;
  final bool seleccionado;
  final VoidCallback alTocar;

  String get _subtitulo => switch (producto.tipoCompra) {
    TipoCompra.recarga => producto.esCanjeable ? 'Código canjeable' : 'Recarga de saldo',
    TipoCompra.pagoServicio => producto.comision > 0
        ? 'Pago de servicio · comisión ${formatoMoneda(producto.comision)}'
        : 'Pago de servicio · sin comisión',
    TipoCompra.noDisponible => 'No disponible para compra',
  };

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final disponible = producto.disponibleParaCompra;
    return Semantics(
      button: true,
      selected: seleccionado,
      child: InkWell(
        onTap: alTocar,
        child: Container(
          decoration: BoxDecoration(
            color: seleccionado ? AppColors.marcaTenue : AppColors.superficie,
            border: const Border(bottom: BorderSide(color: AppColors.lineaSuave)),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      producto.nombre,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: tema.bodyLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                        color: disponible ? AppColors.tinta : AppColors.tintaSuave,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(_subtitulo, style: tema.bodySmall),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              if (producto.tipoCompra == TipoCompra.recarga)
                Text(
                  formatoMoneda(producto.montoFijo!),
                  style: tema.titleMedium?.copyWith(fontFeatures: cifrasTabulares),
                )
              else if (disponible)
                const Icon(Icons.chevron_right, color: AppColors.tintaSuave),
            ],
          ),
        ),
      ),
    );
  }
}
