import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Logo de GestoPago (imagen + nombre). Sobre el panel verde de la marca el
/// ícono va en un recuadro blanco para que no se pierda contra el fondo.
class LogoApp extends StatelessWidget {
  const LogoApp({super.key, this.tamano = 36, this.sobreMarca = false});

  static const ruta = 'assets/images/logo.png';

  final double tamano;
  final bool sobreMarca;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    Widget imagen = Image.asset(ruta, width: tamano, height: tamano);
    if (sobreMarca) {
      imagen = Container(
        padding: EdgeInsets.all(tamano * 0.12),
        decoration: BoxDecoration(
          color: AppColors.superficie,
          borderRadius: BorderRadius.circular(tamano * 0.28),
        ),
        child: imagen,
      );
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        imagen,
        const SizedBox(width: 10),
        Flexible(
          child: Text(
            'GestoPago',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: tema.titleLarge?.copyWith(
              color: sobreMarca ? Colors.white : AppColors.marca,
              fontSize: 22,
            ),
          ),
        ),
      ],
    );
  }
}
