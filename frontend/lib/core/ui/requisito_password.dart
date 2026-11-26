import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Un requisito de contraseña con su marca de cumplido (registro y cambio de contraseña).
class RequisitoPassword extends StatelessWidget {
  const RequisitoPassword({super.key, required this.texto, required this.cumple});
  final String texto;
  final bool cumple;

  @override
  Widget build(BuildContext context) {
    final color = cumple ? AppColors.exito : AppColors.tintaSuave;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          cumple ? Icons.check_circle : Icons.radio_button_unchecked,
          size: 15,
          color: color,
        ),
        const SizedBox(width: 4),
        Text(
          texto,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(color: color),
        ),
      ],
    );
  }
}
