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
          children: [
            // Marco con borde y sombra: el logo (fondo blanco) no se pierde sobre el papel.
            DecoratedBox(
              decoration: BoxDecoration(
                color: AppColors.superficie,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: AppColors.lineaSuave),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x1415201B),
                    blurRadius: 16,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Padding(
                padding: const EdgeInsets.all(10),
                child: Monograma(
                  nombre: producto.servicio,
                  idServicio: producto.idServicio,
                  tamano: 92,
                ),
              ),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    producto.servicio.toUpperCase(),
                    style: tema.labelMedium?.copyWith(
                      color: AppColors.tintaSuave,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    producto.nombre,
                    style: tema.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 10),
                  // Junto al logo grande, en pantallas angostas la etiqueta se reduce en vez de desbordar.
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: producto.disponibleParaCompra
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
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.avisoFondo.withValues(alpha: 0.6),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              children: [
                for (final linea in _condiciones)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 3),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Padding(
                          padding: EdgeInsets.only(top: 2),
                          child: Icon(
                            Icons.info_outline,
                            size: 16,
                            color: AppColors.aviso,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(child: Text(linea, style: tema.bodySmall)),
                      ],
                    ),
                  ),
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
        lineas.add(
          'Indica la referencia de tu recibo y el monto exacto a pagar.',
        );
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
    final (
      String etiqueta,
      String valor,
      String? nota,
    ) = switch (producto.tipoCompra) {
      TipoCompra.recarga => (
        'Precio',
        formatoMoneda(producto.montoFijo!),
        null,
      ),
      TipoCompra.pagoServicio => (
        'Comisión',
        producto.comision > 0
            ? formatoMoneda(producto.comision)
            : 'Sin comisión',
        'Tú indicas el monto a pagar',
      ),
      TipoCompra.noDisponible => (
        'Precio',
        '—',
        'Este producto no se puede pagar',
      ),
    };
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.marca, AppColors.marcaOscura],
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  etiqueta,
                  style: tema.bodyMedium?.copyWith(
                    color: const Color(0xCCFFFFFF),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  valor,
                  style: tema.headlineMedium?.copyWith(
                    fontSize: 36,
                    fontWeight: FontWeight.w800,
                    fontFeatures: cifrasTabulares,
                    color: Colors.white,
                  ),
                ),
                if (nota != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    nota,
                    style: tema.bodySmall?.copyWith(
                      color: const Color(0xCCFFFFFF),
                    ),
                  ),
                ],
              ],
            ),
          ),
          Container(
            width: 52,
            height: 52,
            decoration: const BoxDecoration(
              color: Color(0x22FFFFFF),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.sell_outlined,
              color: Colors.white,
              size: 26,
            ),
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
