import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import 'fondo_acceso.dart';
import 'logo_app.dart';
import 'tema_compacto.dart';

/// Marco común del login y de crear cuenta: fondo, logo, lema, tarjeta verde
/// clara y una franja inferior para el enlace opcional [pie]. Como ambas
/// pantallas lo comparten, la tarjeta mide siempre lo mismo (ancho y alto) y
/// no "salta" al cambiar de una a otra. El alto ocupa el espacio libre de la
/// pantalla, hasta un tope, para que quepa sin hacer scroll.
class MarcoAcceso extends StatelessWidget {
  const MarcoAcceso({
    super.key,
    required this.lema,
    required this.tarjeta,
    this.pie,
  });

  /// Frase bajo el logo (se oculta en pantallas bajas).
  final String lema;

  /// Contenido de la tarjeta; se reparte en el alto con `spaceBetween`.
  final Widget tarjeta;

  /// Enlace bajo la tarjeta; su espacio se reserva aunque no haya.
  final Widget? pie;

  static const anchoMaximo = 520.0;

  static bool _conLema(BuildContext context) =>
      MediaQuery.sizeOf(context).height >= 700;

  /// Alto mínimo de la tarjeta: lo libre de la pantalla menos logo, lema y pie.
  static double altoMinimo(BuildContext context) {
    final libre =
        MediaQuery.sizeOf(context).height -
        MediaQuery.paddingOf(context).vertical;
    return (libre - (_conLema(context) ? 180 : 154)).clamp(0.0, 600.0);
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Theme(
      data: temaCompacto(Theme.of(context)),
      child: Scaffold(
        body: FondoAcceso(
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 12,
                ),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: anchoMaximo),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Center(
                        child: LogoApp(tamano: 34, sobreMarca: true),
                      ),
                      if (_conLema(context)) ...[
                        const SizedBox(height: 6),
                        Text(
                          lema,
                          textAlign: TextAlign.center,
                          style: tema.bodyMedium?.copyWith(
                            color: Colors.white.withValues(alpha: 0.78),
                          ),
                        ),
                      ],
                      const SizedBox(height: 14),
                      Container(
                        constraints: BoxConstraints(
                          minHeight: altoMinimo(context),
                        ),
                        padding: const EdgeInsets.fromLTRB(22, 20, 22, 16),
                        decoration: BoxDecoration(
                          color: AppColors.superficie,
                          borderRadius: BorderRadius.circular(18),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.25),
                              blurRadius: 32,
                              offset: const Offset(0, 14),
                            ),
                          ],
                        ),
                        child: tarjeta,
                      ),
                      const SizedBox(height: 4),
                      SizedBox(height: 36, child: Center(child: pie)),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
