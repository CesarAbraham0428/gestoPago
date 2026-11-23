import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/breakpoints.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/historial_controller.dart';
import '../../domain/entities/compra_historial.dart';
import '../../domain/entities/estado_pago.dart';
import 'estado_visual.dart';
import 'recibo.dart';

/// Pagos del usuario en cualquier estado, con buscador por UPC y filtro por estado. En ancho amplio
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
    // Siempre se vuelve a consultar al entrar: un pago rechazado o pendiente no pasa por la compra.
    // Tras el primer cuadro: notificar durante el build rompería a los demás oyentes del controlador.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => widget.controller.recargar(),
    );
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
          return _DetalleCompra(
            compra: seleccion,
            controller: widget.controller,
          );
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
                      color: AppColors.azul,
                      fondo: AppColors.azulTenue,
                      titulo: 'Elige una compra',
                      detalle: 'Aquí verás su recibo completo.',
                    )
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(32),
                      child: Align(
                        alignment: Alignment.topCenter,
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 520),
                          child: Recibo(
                            datos: DatosRecibo.deHistorial(seleccion),
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
          constraints: const BoxConstraints(maxWidth: 520),
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
    final conFiltros =
        controller.estado == EstadoHistorial.listo &&
        controller.compras.isNotEmpty;
    return Column(
      children: [
        _Buscador(controller: controller),
        if (controller.busquedaActiva)
          Expanded(
            child: _Resultado(
              controller: controller,
              alSeleccionar: alSeleccionar,
            ),
          )
        else ...[
          if (conFiltros) _Filtros(controller: controller),
          Expanded(
            child: _Contenido(
              controller: controller,
              seleccionada: seleccionada,
              alSeleccionar: alSeleccionar,
            ),
          ),
        ],
      ],
    );
  }
}

/// Campo para consultar un pago por su UPC (la referencia de 15 caracteres).
class _Buscador extends StatefulWidget {
  const _Buscador({required this.controller});
  final HistorialController controller;

  @override
  State<_Buscador> createState() => _BuscadorState();
}

class _BuscadorState extends State<_Buscador> {
  final _texto = TextEditingController();

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  void _limpiar() {
    _texto.clear();
    widget.controller.limpiarBusqueda();
  }

  @override
  Widget build(BuildContext context) {
    final activa = widget.controller.busquedaActiva;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Row(
        children: [
          Expanded(child: _campo(activa)),
          const SizedBox(width: 4),
          IconButton(
            tooltip: 'Actualizar pagos',
            icon: const Icon(Icons.refresh),
            onPressed: widget.controller.estado == EstadoHistorial.cargando
                ? null
                : widget.controller.recargar,
          ),
        ],
      ),
    );
  }

  Widget _campo(bool activa) {
    return TextField(
      controller: _texto,
      textInputAction: TextInputAction.search,
      textCapitalization: TextCapitalization.characters,
      autocorrect: false,
      inputFormatters: [
        FilteringTextInputFormatter.allow(RegExp('[A-Za-z0-9]')),
        LengthLimitingTextInputFormatter(15),
        TextInputFormatter.withFunction(
          (anterior, nuevo) => nuevo.copyWith(text: nuevo.text.toUpperCase()),
        ),
      ],
      decoration: InputDecoration(
        hintText: 'Buscar un pago por su UPC',
        prefixIcon: const Icon(Icons.search),
        suffixIcon: activa
            ? IconButton(
                tooltip: 'Limpiar búsqueda',
                icon: const Icon(Icons.close),
                onPressed: _limpiar,
              )
            : IconButton(
                tooltip: 'Buscar pago',
                icon: const Icon(Icons.arrow_forward),
                onPressed: () => widget.controller.buscar(_texto.text),
              ),
      ),
      onSubmitted: widget.controller.buscar,
    );
  }
}

class _Filtros extends StatelessWidget {
  const _Filtros({required this.controller});
  final HistorialController controller;

  static const _etiquetas = {
    FiltroPagos.todos: 'Todos',
    FiltroPagos.aprobados: 'Aprobados',
    FiltroPagos.enProceso: 'En proceso',
    FiltroPagos.rechazados: 'Rechazados',
  };

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Row(
        children: [
          for (final f in FiltroPagos.values) ...[
            ChoiceChip(
              label: Text(_etiquetas[f]!),
              selected: controller.filtro == f,
              onSelected: (_) => controller.filtrar(f),
            ),
            const SizedBox(width: 8),
          ],
        ],
      ),
    );
  }
}

/// Respuesta de la consulta por UPC: estado, mensaje del servicio y datos.
class _Resultado extends StatelessWidget {
  const _Resultado({required this.controller, required this.alSeleccionar});
  final HistorialController controller;
  final ValueChanged<String> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    if (controller.buscando) {
      return const VistaCargando(mensaje: 'Consultando el pago');
    }
    final error = controller.errorBusqueda;
    if (error != null) {
      return VistaMensaje(
        icono: Icons.search_off,
        color: AppColors.coral,
        fondo: AppColors.coralTenue,
        titulo: 'No encontramos ese pago',
        detalle: error,
      );
    }
    final r = controller.resultado!;
    final visual = estadoVisual(r.categoria);
    final enLista = controller.porReferencia(r.upc);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        AvisoEstado(
          tono: visual.tono,
          icono: visual.icono,
          titulo: visual.texto,
          texto: r.mensaje,
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          decoration: BoxDecoration(
            color: AppColors.superficie,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.lineaSuave),
          ),
          child: Column(
            children: [
              FilaDato(etiqueta: 'UPC', valor: r.upc),
              if (r.monto != null)
                FilaDato(etiqueta: 'Importe', valor: formatoMoneda(r.monto!)),
              if (r.comision != null && r.comision! > 0)
                FilaDato(
                  etiqueta: 'Comisión',
                  valor: formatoMoneda(r.comision!),
                ),
              if (r.referencia != null)
                FilaDato(etiqueta: 'Referencia de pago', valor: r.referencia!),
              if (r.idTx != null)
                FilaDato(etiqueta: 'Transacción', valor: '${r.idTx}'),
              if (r.numAutorizacion != null)
                FilaDato(etiqueta: 'Autorización', valor: r.numAutorizacion!),
            ],
          ),
        ),
        if (enLista != null) ...[
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () => alSeleccionar(enLista.upc),
            icon: const Icon(Icons.receipt_long_outlined, size: 18),
            label: const Text('Ver recibo'),
          ),
        ],
      ],
    );
  }
}

class _Contenido extends StatelessWidget {
  const _Contenido({
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
        return const VistaCargando(mensaje: 'Cargando tus pagos');
      case EstadoHistorial.error:
        return VistaMensaje(
          icono: Icons.cloud_off_outlined,
          color: AppColors.coral,
          fondo: AppColors.coralTenue,
          titulo: 'No pudimos cargar tus pagos',
          detalle:
              controller.error ?? 'Revisa tu conexión e inténtalo de nuevo.',
          accion: controller.recargar,
        );
      case EstadoHistorial.listo:
        break;
    }
    if (controller.compras.isEmpty) {
      return const VistaMensaje(
        icono: Icons.receipt_long_outlined,
        color: AppColors.azul,
        fondo: AppColors.azulTenue,
        titulo: 'Aún no tienes pagos',
        detalle: 'Cuando hagas un pago, aparecerá aquí con su estado.',
      );
    }
    final compras = controller.comprasFiltradas;
    if (compras.isEmpty && !controller.hayMas) {
      return const VistaMensaje(
        icono: Icons.filter_list_off,
        titulo: 'Sin pagos con este estado',
        detalle: 'Prueba con otro filtro.',
      );
    }
    return RefreshIndicator(
      onRefresh: controller.recargar,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
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
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: AppColors.rechazo),
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
    final visual = estadoVisual(compra.categoria);
    return Semantics(
      button: true,
      selected: seleccionada,
      child: InkWell(
        onTap: alTocar,
        child: Container(
          decoration: BoxDecoration(
            color: seleccionada ? AppColors.marcaTenue : AppColors.superficie,
            border: const Border(
              bottom: BorderSide(color: AppColors.lineaSuave),
            ),
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
                      style: tema.bodyLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(
                          formatoFechaHora(compra.fecha),
                          style: tema.bodySmall,
                        ),
                        EtiquetaEstado(
                          texto: visual.texto,
                          tono: visual.tono,
                          icono: visual.icono,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Text(
                formatoMoneda(compra.total),
                style: tema.titleMedium?.copyWith(
                  fontFeatures: cifrasTabulares,
                  decoration: compra.categoria == CategoriaPago.rechazado
                      ? TextDecoration.lineThrough
                      : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
