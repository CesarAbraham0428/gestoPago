import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Fondo de las pantallas de acceso (login y crear cuenta): degradado verde con
/// círculos suaves con los colores de las secciones de la app. Solo decora.
class FondoAcceso extends StatelessWidget {
  const FondoAcceso({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [AppColors.bosqueClaro, AppColors.bosque],
      ),
    ),
    child: CustomPaint(painter: const _FondoDecorado(), child: child),
  );
}

class _FondoDecorado extends CustomPainter {
  const _FondoDecorado();

  @override
  void paint(Canvas canvas, Size s) {
    void circulo(double x, double y, double r, Color c, double alfa) =>
        canvas.drawCircle(
          Offset(s.width * x, s.height * y),
          r,
          Paint()..color = c.withValues(alpha: alfa),
        );
    circulo(0.08, 0.12, 180, AppColors.turquesa, 0.28);
    circulo(0.95, 0.85, 220, AppColors.azul, 0.20);
    circulo(0.88, 0.10, 70, AppColors.coral, 0.22);
    circulo(0.12, 0.92, 90, AppColors.ciruela, 0.22);
    final anillo = Paint()
      ..color = Colors.white.withValues(alpha: 0.07)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    canvas
      ..drawCircle(Offset(s.width * 0.82, s.height * 0.35), 140, anillo)
      ..drawCircle(Offset(s.width * 0.20, s.height * 0.62), 110, anillo);
  }

  @override
  bool shouldRepaint(_FondoDecorado oldDelegate) => false;
}
