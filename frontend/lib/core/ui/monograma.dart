import 'package:flutter/material.dart';

/// El catálogo de GestoPago no incluye imágenes. Cada servicio se identifica
/// con sus iniciales sobre un color estable derivado del nombre, de modo que
/// "Telcel" se ve igual en el catálogo, el detalle y el historial.
class Monograma extends StatelessWidget {
  const Monograma({super.key, required this.nombre, this.tamano = 44});

  final String nombre;
  final double tamano;

  static const _fondos = [
    Color(0xFF1F4E5F), Color(0xFF5B3A29), Color(0xFF3D4A7A), Color(0xFF6B2D4A),
    Color(0xFF2F5D3A), Color(0xFF7A4E12), Color(0xFF4A3A6B), Color(0xFF214E6B),
  ];

  String get _iniciales {
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

  Color get _fondo {
    var hash = 0;
    for (final unidad in nombre.toLowerCase().codeUnits) {
      hash = (hash * 31 + unidad) & 0x7fffffff;
    }
    return _fondos[hash % _fondos.length];
  }

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: Container(
        width: tamano,
        height: tamano,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: _fondo,
          borderRadius: BorderRadius.circular(tamano * 0.2),
        ),
        child: Text(
          _iniciales,
          style: TextStyle(
            color: Colors.white,
            fontSize: tamano * 0.36,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.2,
          ),
        ),
      ),
    );
  }
}
