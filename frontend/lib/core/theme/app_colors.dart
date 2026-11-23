import 'package:flutter/material.dart';

/// Paleta de GestoPago. Un verde tinta como única marca; el resto es neutro
/// con un ligero tinte verde. Los colores de estado solo comunican estado.
class AppColors {
  const AppColors._(); // coverage:ignore-line

  static const papel = Color(0xFFF3F5F2);
  static const superficie = Color(0xFFFFFFFF);
  static const tinta = Color(0xFF15201B);
  static const tintaSuave = Color(0xFF55625B);
  static const linea = Color(0xFFD8DFD9);
  static const lineaSuave = Color(0xFFE8ECE8);

  static const marca = Color(0xFF0F5C47);
  static const marcaOscura = Color(0xFF0A4435);
  static const marcaTenue = Color(0xFFDDEBE5);

  /// Fondo de la barra lateral web: verde bosque profundo.
  static const bosque = Color(0xFF0B3B2E);
  static const bosqueClaro = Color(0xFF14513F);

  /// Azul para "Mis compras" y datos de empleo.
  static const azul = Color(0xFF2470C9);
  static const azulTenue = Color(0xFFDFEBF9);

  /// Un color por sección para iconos e ilustraciones.
  static const turquesa = Color(0xFF0E8A8A);
  static const turquesaTenue = Color(0xFFD9F1EF);
  static const coral = Color(0xFFD9614C);
  static const coralTenue = Color(0xFFFBE4DF);
  static const indigo = Color(0xFF4F5BD5);
  static const indigoTenue = Color(0xFFE4E6FA);
  static const ciruela = Color(0xFF9B4F96);
  static const ciruelaTenue = Color(0xFFF3E3F2);

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
