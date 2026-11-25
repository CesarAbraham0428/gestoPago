import '../../core/format/formatters.dart';
import '../../core/validation/texto_seguro.dart';

/// Validaciones previas al envío del registro. Replican las reglas de
/// `ClienteRequest` de la API para evitar viajes inútiles; el servidor sigue
/// siendo la autoridad.
class ValidadoresRegistro {
  const ValidadoresRegistro._(); // coverage:ignore-line

  static const _obligatorio = 'Este campo es obligatorio.';

  static final _letras = RegExp(r'^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+$');
  static final _curp = RegExp(
    r'^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM]'
    r'(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)'
    r'[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$',
  );
  static final _rfc = RegExp(
    r'^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$',
  );

  static String? requerido(String? v, {int min = 1, int max = 250}) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return _obligatorio;
    final inseguro = TextoSeguro.validar(v);
    if (inseguro != null) return inseguro;
    if (t.length < min) return 'Mínimo $min caracteres.';
    return t.length > max ? 'Máximo $max caracteres.' : null;
  }

  /// Nombre o apellido: solo letras (sin espacios), de [min] a 50 caracteres.
  /// Nombres y apellidos piden 3 por defecto.
  static String? nombre(String? v, {bool opcional = false, int min = 3}) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return opcional ? null : _obligatorio;
    return t.length >= min && t.length <= 50 && _letras.hasMatch(v!)
        ? null
        : 'Solo letras, de $min a 50 caracteres.';
  }

  static String? curp(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return _obligatorio;
    return _curp.hasMatch(t.toUpperCase())
        ? null
        : 'Revisa la CURP: son 18 caracteres.';
  }

  static String? rfc(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return _obligatorio;
    return _rfc.hasMatch(t.toUpperCase())
        ? null
        : 'Revisa el RFC: son 12 o 13 caracteres.';
  }

  static String? telefono(String? v, {bool opcional = false}) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return opcional ? null : _obligatorio;
    return RegExp(r'^\d{10}$').hasMatch(t) ? null : 'Debe tener 10 dígitos.';
  }

  static String? codigoPostal(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return _obligatorio;
    return RegExp(r'^\d{5}$').hasMatch(t) ? null : 'Debe tener 5 dígitos.';
  }

  /// Correo con la misma regla que la restricción `ck_clientes_correo` de la
  /// base de datos (el servidor además aplica `@Email` de Jakarta).
  /// Más estricta que la de la base de datos: sin comillas, `;`, `=` ni demás símbolos.
  static final _correo = RegExp(
    r'^[A-Za-z0-9._-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$',
  );

  static String? correo(String? v) {
    final t = (v ?? '').trim();
    if (t.isEmpty) return _obligatorio;
    return t.length > 100 || !_correo.hasMatch(t)
        ? 'Revisa el correo: debe verse como nombre@dominio.com.'
        : null;
  }

  /// Fecha de nacimiento más reciente permitida: hace exactamente 18 años
  /// (igual que `minusYears(18)` del servidor; el 29 de febrero cae en el 28).
  static DateTime fechaMaximaNacimiento(DateTime hoy) {
    final anio = hoy.year - 18;
    final bisiesto = (anio % 4 == 0 && anio % 100 != 0) || anio % 400 == 0;
    final dia = hoy.month == 2 && hoy.day == 29 && !bisiesto ? 28 : hoy.day;
    return DateTime(anio, hoy.month, dia);
  }

  static String? fechaNacimiento(DateTime? fecha, {DateTime? hoy}) {
    if (fecha == null) {
      return 'Selecciona tu fecha de nacimiento (18 años o más).';
    }
    return fecha.isAfter(fechaMaximaNacimiento(hoy ?? DateTime.now()))
        ? 'Debes tener al menos 18 años.'
        : null;
  }

  static String? ingreso(String? v, {bool opcional = false}) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return opcional ? null : _obligatorio;
    final monto = parseMonto(t);
    return monto == null || monto <= 0
        ? 'Escribe un monto mayor a 0 (máximo 2 decimales).'
        : null;
  }

  /// Cada requisito de la contraseña con su cumplimiento, para mostrarlos en vivo.
  static List<(String, bool)> requisitosPassword(String p) => [
    ('8 a 12 caracteres', p.length >= 8 && p.length <= 12),
    ('Mayúscula', RegExp('[A-Z]').hasMatch(p)),
    ('Minúscula', RegExp('[a-z]').hasMatch(p)),
    ('Número', RegExp('[0-9]').hasMatch(p)),
    ('Especial', RegExp(r'[^A-Za-z0-9\s]').hasMatch(p)),
  ];

  static String? password(String? v) {
    final p = v ?? '';
    if (p.isEmpty) return _obligatorio;
    return requisitosPassword(p).every((r) => r.$2)
        ? null
        : 'La contraseña no cumple todos los requisitos.';
  }

  static String? confirmacion(String? v, String password) {
    if ((v ?? '').isEmpty) return _obligatorio;
    return v == password ? null : 'Las contraseñas no coinciden.';
  }
}
