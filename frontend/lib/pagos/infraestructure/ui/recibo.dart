import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../domain/entities/compra_historial.dart';
import '../../domain/entities/comprobante.dart';
import '../../domain/entities/estado_pago.dart';
import '../../domain/entities/solicitud_compra.dart';
import 'estado_visual.dart';

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
    this.estado = 'APROBADO',
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

  /// PENDIENTE, EN_REVISION, APROBADO o RECHAZADO; define el encabezado.
  final String estado;

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
      estado: h.estado,
    );
  }
}

/// Recibo de un pago (por omisión, una compra confirmada), armado como un ticket: cabecera de marca,
/// muescas laterales sobre la línea de corte, borde dentado abajo y código de
/// barras. Si el pago no está aprobado, la cabecera lo dice (en proceso o rechazado).
class Recibo extends StatelessWidget {
  const Recibo({super.key, required this.datos, this.titulo});

  final DatosRecibo datos;

  /// Encabezado; si es nulo se deduce del estado del pago.
  final String? titulo;

  static const _diente = 8.0;
  static const _radioMuesca = 10.0;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Seccion(
          superior: true,
          padding: EdgeInsets.zero,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _Cabecera(datos: datos, titulo: titulo),
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Monograma(nombre: datos.servicio ?? datos.titulo, tamano: 44),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(datos.titulo, style: tema.titleMedium),
                              if (datos.servicio != null)
                                Text(datos.servicio!, style: tema.bodySmall),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    const _Perforacion(),
                    const SizedBox(height: 8),
                    if (datos.destino != null && datos.destinoEtiqueta != null)
                      FilaDato(etiqueta: datos.destinoEtiqueta!, valor: datos.destino!),
                    FilaDato(etiqueta: 'Importe', valor: formatoMoneda(datos.monto)),
                    if (datos.comision > 0) ...[
                      FilaDato(etiqueta: 'Comisión', valor: formatoMoneda(datos.comision)),
                      FilaDato(
                        etiqueta: 'Total',
                        valor: formatoMoneda(datos.total),
                        destacado: true,
                      ),
                    ],
                    if (datos.cuenta != null)
                      FilaDato(etiqueta: 'Cuenta', valor: datos.cuenta!),
                    if (datos.saldoDisponible != null)
                      FilaDato(
                        etiqueta: 'Saldo disponible',
                        valor: formatoMoneda(datos.saldoDisponible!),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        _Seccion(
          superior: false,
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 20 + _diente),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (datos.referenciaOperacion != null)
                _FilaCopiable(etiqueta: 'Referencia', valor: datos.referenciaOperacion!),
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
      ],
    );
  }
}

/// Banda de marca con el resultado: lo primero que se lee del recibo.
class _Cabecera extends StatelessWidget {
  const _Cabecera({required this.datos, required this.titulo});
  final DatosRecibo datos;
  final String? titulo;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final visual = estadoVisual(categoriaDeEstado(datos.estado));
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: visual.colores,
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            right: -14,
            top: -14,
            child: Icon(visual.iconoGrande, size: 130, color: const Color(0x14FFFFFF)),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 22, 24, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: const BoxDecoration(
                        color: Color(0x33FFFFFF),
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        switch (categoriaDeEstado(datos.estado)) {
                          CategoriaPago.aprobado => Icons.check,
                          CategoriaPago.enProceso => Icons.schedule,
                          CategoriaPago.rechazado => Icons.close,
                        },
                        color: Colors.white,
                        size: 18,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      titulo ?? visual.titulo,
                      style: tema.titleMedium?.copyWith(color: Colors.white),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Text(
                  formatoMoneda(datos.total),
                  style: tema.headlineMedium?.copyWith(
                    color: Colors.white,
                    fontSize: 40,
                    fontWeight: FontWeight.w800,
                    fontFeatures: cifrasTabulares,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  formatoFechaHora(datos.fecha),
                  style: tema.bodySmall?.copyWith(color: const Color(0xCCFFFFFF)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Una mitad del ticket: sombra, recorte con la forma y borde fino.
class _Seccion extends StatelessWidget {
  const _Seccion({required this.superior, required this.padding, required this.child});
  final bool superior;
  final EdgeInsets padding;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _SombraTicket(superior),
      foregroundPainter: _BordeTicket(superior),
      child: ClipPath(
        clipper: _FormaTicket(superior),
        child: Container(color: AppColors.superficie, padding: padding, child: child),
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

void _punteada(Canvas canvas, double desde, double hasta) {
  final pincel = Paint()
    ..color = AppColors.linea
    ..strokeWidth = 1.5;
  for (var x = desde; x < hasta; x += 8) {
    canvas.drawLine(Offset(x, 0), Offset(math.min(x + 4, hasta), 0), pincel);
  }
}

class _LineaPunteada extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) => _punteada(canvas, 0, size.width);

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Contorno de cada mitad. La superior tiene muescas en las esquinas de abajo;
/// la inferior, en las de arriba y el borde dentado al pie. Con `relleno` falso
/// se omite el tramo compartido (lo cubre la línea punteada) y no se cierra.
Path _trazoTicket(Size size, {required bool superior, bool relleno = true}) {
  const r = Recibo._radioMuesca;
  const d = Recibo._diente;
  final w = size.width;
  final h = size.height;
  final ruta = Path();
  void tramo(double x, double y) => relleno ? ruta.lineTo(x, y) : ruta.moveTo(x, y);
  void muesca(double x, double y) =>
      ruta.arcToPoint(Offset(x, y), radius: const Radius.circular(r), clockwise: false);

  if (superior) {
    ruta
      ..moveTo(0, 0)
      ..lineTo(w, 0)
      ..lineTo(w, h - r);
    muesca(w - r, h);
    tramo(r, h);
    muesca(0, h - r);
    ruta.lineTo(0, 0);
  } else {
    ruta.moveTo(0, r);
    muesca(r, 0);
    tramo(w - r, 0);
    muesca(w, r);
    ruta.lineTo(w, h - d);
    final dientes = (w / (d * 2)).round().clamp(1, 1000);
    final paso = w / dientes;
    // Puntas suavizadas: la curva pasa por la punta sin dejar un pico afilado.
    const suavizado = 0.28;
    for (var i = 0; i < dientes; i++) {
      final x = w - i * paso;
      final punta = Offset(x - paso / 2, h);
      final valle = Offset(x - paso, h - d);
      ruta
        ..lineTo(
          x + (punta.dx - x) * (1 - suavizado),
          h - d + (d * (1 - suavizado)),
        )
        ..quadraticBezierTo(
          punta.dx,
          punta.dy + 1,
          punta.dx + (valle.dx - punta.dx) * suavizado,
          punta.dy + (valle.dy - punta.dy) * suavizado,
        )
        ..lineTo(valle.dx, valle.dy);
    }
    ruta.lineTo(0, r);
  }
  if (relleno) ruta.close();
  return ruta;
}

class _FormaTicket extends CustomClipper<Path> {
  _FormaTicket(this.superior);
  final bool superior;

  @override
  Path getClip(Size size) => _trazoTicket(size, superior: superior);

  @override
  bool shouldReclip(covariant _FormaTicket oldClipper) => oldClipper.superior != superior;
}

class _SombraTicket extends CustomPainter {
  _SombraTicket(this.superior);
  final bool superior;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawShadow(
      _trazoTicket(size, superior: superior),
      const Color(0x3015201B),
      3,
      false,
    );
  }

  @override
  bool shouldRepaint(covariant _SombraTicket oldDelegate) => oldDelegate.superior != superior;
}

class _BordeTicket extends CustomPainter {
  _BordeTicket(this.superior);
  final bool superior;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawPath(
      _trazoTicket(size, superior: superior, relleno: false),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1
        ..color = AppColors.linea,
    );
    if (!superior) {
      const margen = Recibo._radioMuesca + 8;
      _punteada(canvas, margen, size.width - margen);
    }
  }

  @override
  bool shouldRepaint(covariant _BordeTicket oldDelegate) => oldDelegate.superior != superior;
}
