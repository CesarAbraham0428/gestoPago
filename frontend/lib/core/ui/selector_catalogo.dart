import 'package:flutter/material.dart';

/// Lista desplegable ligada a un [TextEditingController]: los formularios arman
/// sus datos desde los controladores, así que elegir una opción escribe en él.
class SelectorCatalogo extends StatelessWidget {
  const SelectorCatalogo({
    super.key,
    required this.controlador,
    required this.etiqueta,
    required this.opciones,
    this.habilitado = true,
    this.opcional = false,
    this.validar,
  });

  final TextEditingController controlador;
  final String etiqueta;
  final List<String> opciones;
  final bool habilitado;

  /// Permite dejar el campo sin valor (se ofrece "Sin especificar").
  final bool opcional;

  /// Regla sobre la opción elegida (null si no hay); por omisión solo exige elegir una si no es [opcional].
  final String? Function(String?)? validar;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controlador,
    builder: (context, _) {
      final actual = controlador.text.isEmpty ? null : controlador.text;
      // Un valor guardado antes del catálogo se muestra tal cual para no perderlo.
      final lista = actual == null || opciones.contains(actual)
          ? opciones
          : [actual, ...opciones];
      return DropdownButtonFormField<String>(
        // La clave lleva el valor: si cambia desde fuera (p. ej. al omitir un paso) se repinta.
        key: ValueKey(actual),
        initialValue: actual,
        isExpanded: true,
        menuMaxHeight: 320,
        decoration: InputDecoration(labelText: etiqueta),
        items: [
          if (opcional)
            const DropdownMenuItem<String>(
              value: null,
              child: Text('Sin especificar'),
            ),
          for (final o in lista)
            DropdownMenuItem(
              value: o,
              child: Text(o, overflow: TextOverflow.ellipsis),
            ),
        ],
        onChanged: habilitado ? (v) => controlador.text = v ?? '' : null,
        validator:
            validar ??
            (v) => v == null && !opcional ? 'Selecciona una opción.' : null,
      );
    },
  );
}
