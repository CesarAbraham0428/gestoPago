import '../../core/format/formatters.dart';

/// Reglas de entrada de `PagoServicioRequest`, repetidas aquí solo para guiar
/// al usuario antes de enviar. El servidor valida de nuevo todo.
class ValidadoresCompra {
  const ValidadoresCompra._(); // coverage:ignore-line

  static String? telefono(String? valor) {
    final texto = (valor ?? '').trim();
    if (texto.isEmpty) return 'Escribe el número de celular.';
    if (!RegExp(r'^\d{10}$').hasMatch(texto)) {
      return 'Debe tener 10 dígitos, sin espacios ni guiones.';
    }
    return null;
  }

  static String? referencia(String? valor) {
    final texto = (valor ?? '').trim();
    if (texto.isEmpty) return 'Escribe la referencia de tu recibo.';
    if (!RegExp(r'^[A-Za-z0-9]{1,100}$').hasMatch(texto)) {
      return 'Usa solo letras y números, sin espacios ni guiones.';
    }
    return null;
  }

  static String? monto(String? valor) {
    final texto = (valor ?? '').trim();
    if (texto.isEmpty) return 'Escribe el monto a pagar.';
    final monto = parseMonto(texto);
    if (monto == null) return 'Usa un número con máximo 2 decimales.';
    if (monto < 0.01) return 'El monto debe ser mayor a cero.';
    return null;
  }

}
