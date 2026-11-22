import 'package:flutter/material.dart';

/// Ilustración vectorial dibujada en código (sin dependencias de SVG): una
/// mancha orgánica de [fondo], anillos y puntos decorativos de [color], y el
/// [icono] dentro de una tarjeta flotante.
class Ilustracion extends StatelessWidget {
  const Ilustracion({
    super.key,
    required this.icono,
    required this.color,
    required this.fondo,
    this.tamano = 132,
  });

  final IconData icono;
  final Color color;
  final Color fondo;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: SizedBox(
        width: tamano,
        height: tamano,
        child: CustomPaint(
          painter: _Fondo(color: color, fondo: fondo),
          child: Center(
            child: Container(
              width: tamano * 0.44,
              height: tamano * 0.44,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(tamano * 0.12),
                boxShadow: [
                  BoxShadow(
                    color: color.withValues(alpha: 0.25),
                    blurRadius: 18,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Icon(icono, color: color, size: tamano * 0.24),
            ),
          ),
        ),
      ),
    );
  }
}

class _Fondo extends CustomPainter {
  const _Fondo({required this.color, required this.fondo});
  final Color color;
  final Color fondo;

  @override
  void paint(Canvas canvas, Size s) {
    final w = s.width;
    final h = s.height;
    // Mancha orgánica.
    final mancha = Path()
      ..moveTo(w * 0.50, h * 0.08)
      ..cubicTo(w * 0.80, h * 0.04, w * 0.98, h * 0.30, w * 0.92, h * 0.56)
      ..cubicTo(w * 0.86, h * 0.84, w * 0.60, h * 0.98, w * 0.36, h * 0.90)
      ..cubicTo(w * 0.10, h * 0.82, w * 0.00, h * 0.56, w * 0.08, h * 0.34)
      ..cubicTo(w * 0.16, h * 0.14, w * 0.30, h * 0.10, w * 0.50, h * 0.08)
      ..close();
    canvas.drawPath(mancha, Paint()..color = fondo);

    // Anillo discontinuo alrededor.
    final anillo = Paint()
      ..color = color.withValues(alpha: 0.35)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6
      ..strokeCap = StrokeCap.round;
    final rect = Rect.fromCircle(
      center: Offset(w / 2, h / 2),
      radius: w * 0.38,
    );
    for (var i = 0; i < 12; i++) {
      canvas.drawArc(rect, i * 0.5236, 0.28, false, anillo);
    }

    // Puntos y destellos decorativos.
    final punto = Paint()..color = color;
    canvas.drawCircle(Offset(w * 0.86, h * 0.20), w * 0.035, punto);
    canvas.drawCircle(
      Offset(w * 0.14, h * 0.76),
      w * 0.025,
      punto..color = color.withValues(alpha: 0.6),
    );
    final destello = Paint()
      ..color = color.withValues(alpha: 0.7)
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    final c = Offset(w * 0.16, h * 0.22);
    final r = w * 0.05;
    canvas
      ..drawLine(c.translate(-r, 0), c.translate(r, 0), destello)
      ..drawLine(c.translate(0, -r), c.translate(0, r), destello);
  }

  @override
  bool shouldRepaint(_Fondo old) => old.color != color || old.fondo != fondo;
}

/// Recuadro de color con un icono, para listas, menús y encabezados.
class IconoEnRecuadro extends StatelessWidget {
  const IconoEnRecuadro({
    super.key,
    required this.icono,
    required this.color,
    required this.fondo,
    this.tamano = 40,
  });

  final IconData icono;
  final Color color;
  final Color fondo;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: tamano,
      height: tamano,
      decoration: BoxDecoration(
        color: fondo,
        borderRadius: BorderRadius.circular(tamano * 0.28),
      ),
      child: Icon(icono, color: color, size: tamano * 0.52),
    );
  }
}
