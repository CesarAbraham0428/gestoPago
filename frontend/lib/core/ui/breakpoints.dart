import 'package:flutter/widgets.dart';

/// Puntos de quiebre compartidos por las dos aplicaciones.
class Breakpoints {
  const Breakpoints._(); // coverage:ignore-line

  /// Desde aquí el catálogo muestra lista y detalle lado a lado.
  static const dosPaneles = 960.0;

  /// Desde aquí la web usa barra lateral en lugar de navegación inferior.
  static const barraLateral = 840.0;

  static bool esAncho(BuildContext context, [double desde = dosPaneles]) =>
      MediaQuery.sizeOf(context).width >= desde;
}
