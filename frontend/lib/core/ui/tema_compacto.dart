import 'package:flutter/material.dart';

/// Variante del tema con campos y botones más bajos, para las pantallas de
/// acceso y registro: el formulario completo debe caber sin hacer scroll.
/// El botón secundario va con fondo blanco para destacar sobre la tarjeta verde.
ThemeData temaCompacto(ThemeData base) => base.copyWith(
  inputDecorationTheme: base.inputDecorationTheme.copyWith(
    isDense: true,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
  ),
  filledButtonTheme: FilledButtonThemeData(
    style: base.filledButtonTheme.style?.merge(
      FilledButton.styleFrom(minimumSize: const Size(64, 44)),
    ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: base.outlinedButtonTheme.style?.merge(
      OutlinedButton.styleFrom(
        minimumSize: const Size(64, 44),
        backgroundColor: Colors.white,
      ),
    ),
  ),
);
