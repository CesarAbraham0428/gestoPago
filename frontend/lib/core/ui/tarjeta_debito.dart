import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../format/formatters.dart';
import '../theme/app_colors.dart';
import '../theme/app_theme.dart';

/// Número de cuenta agrupado de 4 en 4 ("1234 5678 9012 3456 78").
String numeroAgrupado(String numero) =>
    RegExp('.{1,4}').allMatches(numero).map((m) => m[0]).join(' ');

/// Cuenta bancaria dibujada como una tarjeta de débito: número, copiar y saldo.
/// Se usa en "Cuenta bancaria" y en el detalle del producto.
class TarjetaDebito extends StatefulWidget {
  const TarjetaDebito({
    super.key,
    required this.numeroCuenta,
    required this.saldo,
    this.activa = true,
  });

  final String numeroCuenta;
  final double saldo;
  final bool activa;

  @override
  State<TarjetaDebito> createState() => _TarjetaDebitoState();
}

class _TarjetaDebitoState extends State<TarjetaDebito> {
  bool _verSaldo = false; // el saldo arranca oculto

  String get numeroCuenta => widget.numeroCuenta;
  bool get activa => widget.activa;

  Future<void> _copiar(BuildContext context) async {
    final mensajes = ScaffoldMessenger.of(context);
    await Clipboard.setData(ClipboardData(text: numeroCuenta));
    mensajes.showSnackBar(
      const SnackBar(content: Text('Número de cuenta copiado.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final blanco = Colors.white.withValues(alpha: 0.78);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: AspectRatio(
          aspectRatio: 1.586, // proporción de una tarjeta ISO/IEC 7810
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              color: activa ? AppColors.marca : const Color(0xFF3B4540),
              boxShadow: [
                BoxShadow(
                  color: AppColors.bosque.withValues(alpha: 0.25),
                  blurRadius: 18,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(18),
              child: Stack(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 16, 12, 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Text(
                              'GestoPago',
                              style: tema.titleMedium?.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const Spacer(),
                            Text(
                              'Débito',
                              style: tema.bodySmall?.copyWith(color: blanco),
                            ),
                            const SizedBox(width: 6),
                            Icon(
                              Icons.contactless_outlined,
                              color: blanco,
                              size: 22,
                            ),
                            const SizedBox(width: 8),
                          ],
                        ),
                        const _Chip(),
                        Row(
                          children: [
                            Expanded(
                              child: FittedBox(
                                fit: BoxFit.scaleDown,
                                alignment: Alignment.centerLeft,
                                child: SelectableText(
                                  numeroAgrupado(numeroCuenta),
                                  style: tema.titleLarge?.copyWith(
                                    color: Colors.white,
                                    fontFeatures: cifrasTabulares,
                                    letterSpacing: 1.5,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ),
                            IconButton(
                              tooltip: 'Copiar número de cuenta',
                              visualDensity: VisualDensity.compact,
                              iconSize: 20,
                              color: Colors.white,
                              icon: const Icon(Icons.copy_outlined),
                              onPressed: () => _copiar(context),
                            ),
                          ],
                        ),
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'Saldo disponible',
                                      style: tema.bodySmall?.copyWith(
                                        color: blanco,
                                      ),
                                    ),
                                    FittedBox(
                                      fit: BoxFit.scaleDown,
                                      child: Text(
                                        _verSaldo
                                            ? formatoMoneda(widget.saldo)
                                            : r'$ ••••••',
                                        style: tema.titleLarge?.copyWith(
                                          color: Colors.white,
                                          fontFeatures: cifrasTabulares,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              IconButton(
                                tooltip: _verSaldo
                                    ? 'Ocultar saldo'
                                    : 'Mostrar saldo',
                                visualDensity: VisualDensity.compact,
                                iconSize: 20,
                                color: Colors.white,
                                icon: Icon(
                                  _verSaldo
                                      ? Icons.visibility_off_outlined
                                      : Icons.visibility_outlined,
                                ),
                                onPressed: () =>
                                    setState(() => _verSaldo = !_verSaldo),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                cuentaEnmascarada(numeroCuenta),
                                style: tema.bodySmall?.copyWith(
                                  color: blanco,
                                  fontFeatures: cifrasTabulares,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Chip dorado de la tarjeta.
class _Chip extends StatelessWidget {
  const _Chip();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 40,
      height: 30,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(6),
        gradient: const LinearGradient(
          colors: [Color(0xFFE8C872), Color(0xFFB8913A)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: CustomPaint(painter: _LineasChip()),
    );
  }
}

class _LineasChip extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = const Color(0xFF7A5D1E).withValues(alpha: 0.6)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(0, size.height / 2),
      Offset(size.width, size.height / 2),
      p,
    );
    canvas.drawLine(
      Offset(size.width / 3, 0),
      Offset(size.width / 3, size.height),
      p,
    );
    canvas.drawLine(
      Offset(size.width * 2 / 3, 0),
      Offset(size.width * 2 / 3, size.height),
      p,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
