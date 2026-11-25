import 'package:flutter/services.dart';

/// Texto libre sin espacios ni símbolos: solo letras (con acentos y ñ) y números.
/// Primera barrera contra la inyección SQL desde los formularios: comillas,
/// punto y coma, guiones, paréntesis y espacios nunca llegan a la API. El
/// servidor sigue siendo la autoridad (consultas parametrizadas).
class TextoSeguro {
  const TextoSeguro._(); // coverage:ignore-line

  static const _clase = 'A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ';
  static final _valido = RegExp('^[$_clase]*\$');

  /// Impide escribir o pegar cualquier carácter fuera de la lista.
  static final formato = FilteringTextInputFormatter.allow(RegExp('[$_clase]'));

  /// Valida el texto tal cual (sin recortar): un espacio, aunque sea al borde, falla.
  static String? validar(String? v) => _valido.hasMatch(v ?? '')
      ? null
      : 'Solo letras y números, sin espacios ni símbolos.';
}
