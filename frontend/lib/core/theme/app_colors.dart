import 'package:flutter/material.dart';

/// Paleta de GestoPago. Un verde tinta como única marca; el resto es neutro
/// con un ligero tinte verde. Los colores de estado solo comunican estado.
class AppColors {
  const AppColors._();

  static const papel = Color(0xFFF3F5F2);
  static const superficie = Color(0xFFFFFFFF);
  static const tinta = Color(0xFF15201B);
  static const tintaSuave = Color(0xFF55625B);
  static const linea = Color(0xFFD8DFD9);
  static const lineaSuave = Color(0xFFE8ECE8);

  static const marca = Color(0xFF0F5C47);
  static const marcaOscura = Color(0xFF0A4435);
  static const marcaTenue = Color(0xFFDDEBE5);

  static const exito = Color(0xFF14724F);
  static const exitoFondo = Color(0xFFDDEFE6);
  static const pendiente = Color(0xFF8A5A00);
  static const pendienteFondo = Color(0xFFFBEBC8);
  static const rechazo = Color(0xFFA6261C);
  static const rechazoFondo = Color(0xFFFBE3E0);
  static const aviso = Color(0xFF34506A);
  static const avisoFondo = Color(0xFFE1EAF1);
}

enum Tono { exito, pendiente, rechazo, aviso, neutro }

extension TonoColores on Tono {
  Color get texto => switch (this) {
    Tono.exito => AppColors.exito,
    Tono.pendiente => AppColors.pendiente,
    Tono.rechazo => AppColors.rechazo,
    Tono.aviso => AppColors.aviso,
    Tono.neutro => AppColors.tintaSuave,
  };

  Color get fondo => switch (this) {
    Tono.exito => AppColors.exitoFondo,
    Tono.pendiente => AppColors.pendienteFondo,
    Tono.rechazo => AppColors.rechazoFondo,
    Tono.aviso => AppColors.avisoFondo,
    Tono.neutro => AppColors.lineaSuave,
  };
}
