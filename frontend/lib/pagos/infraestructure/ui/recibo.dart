import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../domain/entities/compra_historial.dart';
import '../../domain/entities/comprobante.dart';
import '../../domain/entities/solicitud_compra.dart';

/// Datos que muestra el recibo, sin importar si vienen de una compra recién
/// hecha o del historial.
class DatosRecibo {
  const DatosRecibo({
    required this.titulo,
    required this.servicio,
    required this.fecha,
    required this.monto,
    required this.comision,
    required this.total,
    this.destinoEtiqueta,
    this.destino,
    this.cuenta,
    this.idTx,
    this.numAutorizacion,
    this.referenciaOperacion,
    this.saldoDisponible,
    this.codigoCanje,
    this.leyenda,
  });

  final String titulo;
  final String? servicio;
  final DateTime fecha;
  final double monto;
  final double comision;
  final double total;
  final String? destinoEtiqueta;
  final String? destino;
  final String? cuenta;
  final int? idTx;
  final String? numAutorizacion;

  /// Identificador de la operación (UPC); permite consultarla después.
  final String? referenciaOperacion;
  final double? saldoDisponible;
  final String? codigoCanje;
  final String? leyenda;

  factory DatosRecibo.deComprobante(Comprobante c, SolicitudCompra s) {
    final esRecarga = s.telefono != null;
    return DatosRecibo(
      titulo: s.producto.nombre,
      servicio: s.producto.servicio,
      fecha: c.registradoEn,
      monto: c.monto,
      comision: c.comision,
      total: c.total,
      destinoEtiqueta: esRecarga ? 'Celular' : 'Referencia de pago',
      destino: esRecarga ? formatoTelefono(s.telefono!) : (c.referencia ?? s.referencia),
      cuenta: cuentaEnmascarada(s.numeroCuenta),
      idTx: c.idTx,
      numAutorizacion: c.numAutorizacion,
      saldoDisponible: c.saldoDisponible,
      codigoCanje: c.codigoCanje,
      leyenda: c.leyenda,
    );
  }

  factory DatosRecibo.deHistorial(CompraHistorial h) {
    final telefono = h.telefono;
    final esRecarga = telefono != null && telefono.length == 10;
    return DatosRecibo(
      titulo: h.titulo,
      servicio: h.servicio,
      fecha: h.fecha,
      monto: h.monto,
      comision: h.comision,
      total: h.total,
      destinoEtiqueta: esRecarga
          ? 'Celular'
          : (h.referencia != null ? 'Referencia de pago' : null),
      destino: esRecarga ? formatoTelefono(telefono) : h.referencia,
      idTx: h.idTx,
      numAutorizacion: h.numAutorizacion,
      referenciaOperacion: h.upc,
    );
  }
}

/// Recibo de una compra confirmada. Es el único elemento con borde dentado
/// (como un ticket): solo aparece para compras que el servicio confirmó.
class Recibo extends StatelessWidget {
  const Recibo({super.key, required this.datos, this.titulo = 'Compra confirmada'});

  final DatosRecibo datos;
  final String titulo;

  static const _diente = 8.0;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return CustomPaint(
      foregroundPainter: _BordeRecibo(),
      child: ClipPath(
        clipper: _FormaRecibo(),
        child: Container(
          color: AppColors.superficie,
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 24 + _diente),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  const Icon(Icons.check_circle, color: AppColors.exito, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    titulo,
                    style: tema.titleMedium?.copyWith(color: AppColors.exito),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                formatoMoneda(datos.total),
                style: tema.headlineMedium?.copyWith(
                  fontSize: 36,
                  fontFeatures: cifrasTabulares,
                ),
              ),
              const SizedBox(height: 4),
              Text(formatoFechaHora(datos.fecha), style: tema.bodySmall),
              const SizedBox(height: 16),
              Text(datos.titulo, style: tema.titleMedium),
              if (datos.servicio != null)
                Text(datos.servicio!, style: tema.bodySmall),
              const SizedBox(height: 12),
              const _Perforacion(),
              const SizedBox(height: 8),
              if (datos.destino != null && datos.destinoEtiqueta != null)
                FilaDato(etiqueta: datos.destinoEtiqueta!, valor: datos.destino!),
              FilaDato(etiqueta: 'Importe', valor: formatoMoneda(datos.monto)),
              if (datos.comision > 0)
                FilaDato(etiqueta: 'Comisión', valor: formatoMoneda(datos.comision)),
              if (datos.cuenta != null)
                FilaDato(etiqueta: 'Cuenta', valor: datos.cuenta!),
              if (datos.saldoDisponible != null)
                FilaDato(
                  etiqueta: 'Saldo disponible',
                  valor: formatoMoneda(datos.saldoDisponible!),
                ),
              const SizedBox(height: 4),
              const _Perforacion(),
              const SizedBox(height: 8),
              if (datos.referenciaOperacion != null)
                _FilaCopiable(
                  etiqueta: 'Referencia',
                  valor: datos.referenciaOperacion!,
                ),
              if (datos.idTx != null)
                FilaDato(etiqueta: 'Transacción', valor: '${datos.idTx}'),
              if (datos.numAutorizacion != null)
                FilaDato(etiqueta: 'Autorización', valor: datos.numAutorizacion!),
              if (datos.codigoCanje != null && datos.codigoCanje!.isNotEmpty) ...[
                const SizedBox(height: 8),
                _CodigoCanje(codigo: datos.codigoCanje!, leyenda: datos.leyenda),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _FilaCopiable extends StatelessWidget {
  const _FilaCopiable({required this.etiqueta, required this.valor});
  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            etiqueta,
            style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
          ),
          Row(
            children: [
              Expanded(
                child: SelectableText(
                  valor,
                  style: tema.titleMedium?.copyWith(fontFeatures: cifrasTabulares),
                ),
              ),
              IconButton(
                tooltip: 'Copiar referencia',
                icon: const Icon(Icons.copy_outlined, size: 18),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: valor));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context)
                      ..hideCurrentSnackBar()
                      ..showSnackBar(
                        const SnackBar(content: Text('Referencia copiada')),
                      );
                  }
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _CodigoCanje extends StatelessWidget {
  const _CodigoCanje({required this.codigo, this.leyenda});
  final String codigo;
  final String? leyenda;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.marcaTenue,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Código para canjear', style: tema.bodySmall),
          const SizedBox(height: 4),
          Row(
            children: [
              Expanded(
                child: SelectableText(
                  codigo,
                  style: tema.titleMedium?.copyWith(fontFeatures: cifrasTabulares),
                ),
              ),
              IconButton(
                tooltip: 'Copiar código',
                icon: const Icon(Icons.copy_outlined, size: 18),
                onPressed: () => Clipboard.setData(ClipboardData(text: codigo)),
              ),
            ],
          ),
          if (leyenda != null && leyenda!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(leyenda!, style: tema.bodySmall),
          ],
        ],
      ),
    );
  }
}

class _Perforacion extends StatelessWidget {
  const _Perforacion();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 1,
      width: double.infinity,
      child: CustomPaint(painter: _LineaPunteada()),
    );
  }
}

class _LineaPunteada extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final pincel = Paint()
      ..color = AppColors.linea
      ..strokeWidth = 1.5;
    for (var x = 0.0; x < size.width; x += 8) {
      canvas.drawLine(Offset(x, 0), Offset(x + 4, 0), pincel);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

Path _trazoRecibo(Size size) {
  const d = Recibo._diente;
  final ruta = Path()..moveTo(0, 0)..lineTo(size.width, 0);
  ruta.lineTo(size.width, size.height - d);
  final dientes = (size.width / (d * 2)).round().clamp(1, 1000);
  final paso = size.width / dientes;
  for (var i = 0; i < dientes; i++) {
    final x = size.width - i * paso;
    ruta
      ..lineTo(x - paso / 2, size.height)
      ..lineTo(x - paso, size.height - d);
  }
  ruta.close();
  return ruta;
}

class _FormaRecibo extends CustomClipper<Path> {
  @override
  Path getClip(Size size) => _trazoRecibo(size);

  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}

class _BordeRecibo extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawPath(
      _trazoRecibo(size),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1
        ..color = AppColors.linea,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
