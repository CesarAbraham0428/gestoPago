import 'package:flutter/material.dart';

/// Identifica un servicio con su logo de GestoPago (por `idServicio`) o, si no
/// se conoce el id o el logo no existe, con sus iniciales sobre un color
/// estable derivado del nombre, de modo que "Telcel" se ve igual en todas
/// partes.
class Monograma extends StatelessWidget {
  const Monograma({
    super.key,
    required this.nombre,
    this.tamano = 44,
    this.idServicio,
  });

  final String nombre;
  final double tamano;
  final int? idServicio;

  /// Logos públicos de GestoPago: no requieren token.
  static String urlLogo(int idServicio) =>
      'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/$idServicio.png';

  static const _fondos = [
    Color(0xFF1F4E5F),
    Color(0xFF5B3A29),
    Color(0xFF3D4A7A),
    Color(0xFF6B2D4A),
    Color(0xFF2F5D3A),
    Color(0xFF7A4E12),
    Color(0xFF4A3A6B),
    Color(0xFF214E6B),
  ];

  static String iniciales(String nombre) {
    final partes = nombre
        .trim()
        .split(RegExp(r'\s+'))
        .where((p) => RegExp(r'^[A-Za-zÁÉÍÓÚÑáéíóúñ0-9]').hasMatch(p))
        .toList();
    if (partes.isEmpty) return '?';
    if (partes.length == 1) {
      final p = partes.first;
      return p.substring(0, p.length >= 2 ? 2 : 1).toUpperCase();
    }
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }

  static Color colorDe(String nombre) {
    var hash = 0;
    for (final unidad in nombre.toLowerCase().codeUnits) {
      hash = (hash * 31 + unidad) & 0x7fffffff;
    }
    return _fondos[hash % _fondos.length];
  }

  Widget _iniciales() => Container(
    width: tamano,
    height: tamano,
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: colorDe(nombre),
      borderRadius: BorderRadius.circular(tamano * 0.2),
    ),
    child: Text(
      iniciales(nombre),
      style: TextStyle(
        color: Colors.white,
        fontSize: tamano * 0.36,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.2,
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final id = idServicio;
    if (id == null) return ExcludeSemantics(child: _iniciales());
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: tamano,
        child: Image.network(
          urlLogo(id),
          // Con <img> en web, sin clave puede quedarse el logo anterior.
          key: ValueKey(id),
          fit: BoxFit.contain,
          // El servidor no envía CORS: en web se pinta con <img> para que cargue.
          webHtmlElementStrategy: WebHtmlElementStrategy.prefer,
          frameBuilder: (_, imagen, frame, sincrono) =>
              frame == null && !sincrono
              ? _iniciales()
              : Container(
                  padding: EdgeInsets.all(tamano * 0.1),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(tamano * 0.2),
                  ),
                  child: imagen,
                ),
          errorBuilder: (_, _, _) => _iniciales(),
        ),
      ),
    );
  }
}
