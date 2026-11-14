import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../domain/entities/producto.dart';

/// Ficha del producto: identidad, precio y condiciones. La acción de compra se
/// recibe como widget para que este módulo no dependa del de pagos.
class DetalleProducto extends StatelessWidget {
  const DetalleProducto({
    super.key,
    required this.producto,
    required this.accionCompra,
  });

  final Producto producto;
  final Widget accionCompra;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Monograma(nombre: producto.servicio, tamano: 56),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(producto.servicio, style: tema.bodySmall),
                  const SizedBox(height: 2),
                  Text(producto.nombre, style: tema.titleLarge),
                  const SizedBox(height: 8),
                  producto.disponibleParaCompra
                      ? const EtiquetaEstado(
                          texto: 'Disponible para compra',
                          tono: Tono.exito,
                          icono: Icons.check_circle_outline,
                        )
                      : const EtiquetaEstado(
                          texto: 'No disponible para compra',
                          tono: Tono.neutro,
                          icono: Icons.block,
                        ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        _Precio(producto: producto),
        if (_condiciones.isNotEmpty) ...[
          const SizedBox(height: 16),
          for (final linea in _condiciones)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(top: 2),
                    child: Icon(Icons.info_outline, size: 16, color: AppColors.tintaSuave),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: Text(linea, style: tema.bodySmall)),
                ],
              ),
            ),
        ],
        const SizedBox(height: 24),
        if (producto.disponibleParaCompra)
          accionCompra
        else
          const _NoDisponible(),
      ],
    );
  }

  List<String> get _condiciones {
    final lineas = <String>[];
    switch (producto.tipoCompra) {
      case TipoCompra.recarga:
        lineas.add(
          producto.esCanjeable
              ? 'Recibirás un código para canjear al terminar la compra.'
              : 'La recarga se aplica al número de celular que indiques.',
        );
      case TipoCompra.pagoServicio:
        lineas.add('Indica la referencia de tu recibo y el monto exacto a pagar.');
        if (producto.comision > 0) {
          lineas.add('La comisión se suma al importe de tu pago.');
        }
      case TipoCompra.noDisponible:
        break;
    }
    final leyenda = producto.leyenda;
    if (leyenda != null && leyenda.isNotEmpty) lineas.add(leyenda);
    return lineas;
  }
}

class _Precio extends StatelessWidget {
  const _Precio({required this.producto});
  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final (etiqueta, valor, nota) = switch (producto.tipoCompra) {
      TipoCompra.recarga => (
        'Precio',
        formatoMoneda(producto.montoFijo!),
        'Monto fijo',
      ),
      TipoCompra.pagoServicio => (
        'Comisión',
        producto.comision > 0 ? formatoMoneda(producto.comision) : 'Sin comisión',
        'Tú indicas el monto a pagar',
      ),
      TipoCompra.noDisponible => ('Precio', '—', 'Este producto no se puede pagar'),
    };
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.superficie,
        border: Border.all(color: AppColors.linea),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(etiqueta, style: tema.bodySmall),
                const SizedBox(height: 4),
                Text(
                  valor,
                  style: tema.headlineMedium?.copyWith(fontFeatures: cifrasTabulares),
                ),
              ],
            ),
          ),
          Flexible(
            child: Text(nota, style: tema.bodySmall, textAlign: TextAlign.right),
          ),
        ],
      ),
    );
  }
}

class _NoDisponible extends StatelessWidget {
  const _NoDisponible();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lineaSuave,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        'Este producto es de consulta y no se puede comprar desde la aplicación. '
        'Elige otro del catálogo.',
        style: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }
}
