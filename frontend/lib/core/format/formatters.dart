import 'package:intl/intl.dart';

const _locale = 'es_MX';

final _moneda = NumberFormat.currency(locale: _locale, symbol: r'$');
final _fecha = DateFormat("d 'de' MMM y", _locale);
final _hora = DateFormat('HH:mm', _locale);

String formatoMoneda(num valor) => _moneda.format(valor);

String formatoFecha(DateTime fecha) => _fecha.format(fecha.toLocal());

String formatoHora(DateTime fecha) => _hora.format(fecha.toLocal());

String formatoFechaHora(DateTime fecha) {
  final local = fecha.toLocal();
  return '${_fecha.format(local)}, ${_hora.format(local)}';
}

/// "5512345678" -> "55 1234 5678"
String formatoTelefono(String telefono) {
  if (telefono.length != 10) return telefono;
  return '${telefono.substring(0, 2)} ${telefono.substring(2, 6)} ${telefono.substring(6)}';
}

/// Muestra solo los últimos 4 dígitos de una cuenta: "•••• 4821".
String cuentaEnmascarada(String numero) {
  if (numero.length < 4) return numero;
  return '•••• ${numero.substring(numero.length - 4)}';
}

/// Convierte "125.5" o "125,50" a 125.5. Devuelve null si no es un monto válido
/// (más de 2 decimales, negativo, vacío o no numérico).
double? parseMonto(String texto) {
  final limpio = texto.trim().replaceAll(',', '.');
  if (!RegExp(r'^\d{1,14}(\.\d{1,2})?$').hasMatch(limpio)) return null;
  return double.tryParse(limpio);
}
