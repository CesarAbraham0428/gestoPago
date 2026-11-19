import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/app_colors.dart';

/// Código de un solo uso con un cuadro por dígito.
///
/// Por debajo hay un único [TextField] invisible que recibe el teclado, de modo
/// que pegar el código, el autocompletado de SMS/correo (`oneTimeCode`) y el
/// borrado funcionan como en cualquier campo; los cuadros solo lo dibujan.
class CampoCodigo extends StatefulWidget {
  const CampoCodigo({
    super.key,
    required this.controller,
    this.longitud = 6,
    this.enabled = true,
    this.autofocus = false,
    this.validator,
    this.alEnviar,
  });

  /// Clave del campo de texto real, para las pruebas.
  static const claveCampo = Key('campo-codigo');

  final TextEditingController controller;
  final int longitud;
  final bool enabled;
  final bool autofocus;
  final FormFieldValidator<String>? validator;
  final VoidCallback? alEnviar;

  @override
  State<CampoCodigo> createState() => _CampoCodigoState();
}

class _CampoCodigoState extends State<CampoCodigo> {
  final _foco = FocusNode();

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_redibujar);
    _foco.addListener(_redibujar);
  }

  @override
  void didUpdateWidget(CampoCodigo anterior) {
    super.didUpdateWidget(anterior);
    if (anterior.controller != widget.controller) {
      anterior.controller.removeListener(_redibujar);
      widget.controller.addListener(_redibujar);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_redibujar);
    _foco.dispose();
    super.dispose();
  }

  void _redibujar() {
    // Los cuadros pintan el texto por posición: el cursor siempre va al final
    // para que un toque en un cuadro intermedio no inserte dígitos en medio.
    final valor = widget.controller.value;
    final fin = valor.text.length;
    if (!valor.selection.isCollapsed || valor.selection.baseOffset != fin) {
      widget.controller.selection = TextSelection.collapsed(offset: fin);
    }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return FormField<String>(
      validator: (_) => widget.validator?.call(widget.controller.text),
      builder: (estado) {
        final texto = widget.controller.text;
        final error = estado.errorText;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              label: 'Código de verificación de ${widget.longitud} dígitos',
              child: SizedBox(
                height: 60,
                child: Stack(
                  children: [
                    Row(
                      children: [
                        for (var i = 0; i < widget.longitud; i++) ...[
                          if (i > 0) const SizedBox(width: 8),
                          Expanded(
                            child: _Cuadro(
                              digito: i < texto.length ? texto[i] : '',
                              activo: _foco.hasFocus &&
                                  widget.enabled &&
                                  (i == texto.length ||
                                      (texto.length == widget.longitud &&
                                          i == widget.longitud - 1)),
                              conError: error != null,
                              habilitado: widget.enabled,
                            ),
                          ),
                        ],
                      ],
                    ),
                    // Campo real: cubre los cuadros para recibir toques y teclado.
                    Positioned.fill(
                      child: Opacity(
                        opacity: 0,
                        child: TextField(
                          key: CampoCodigo.claveCampo,
                          controller: widget.controller,
                          focusNode: _foco,
                          readOnly: !widget.enabled, // no `enabled`: desactivar quita el foco y en web deja de recibir texto
                          autofocus: widget.autofocus,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          autofillHints: const [AutofillHints.oneTimeCode],
                          enableSuggestions: false,
                          autocorrect: false,
                          showCursor: false,
                          // Debe quedar activa: sin ella Flutter bloquea pegar
                          // (Ctrl+V en web y el menú "Pegar" en móvil).
                          enableInteractiveSelection: true,
                          inputFormatters: [FormatoCodigo(widget.longitud)],
                          decoration: const InputDecoration.collapsed(hintText: ''),
                          onChanged: estado.didChange,
                          onSubmitted: (_) => widget.alEnviar?.call(),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (error != null) ...[
              const SizedBox(height: 8),
              Text(error, style: tema.bodySmall?.copyWith(color: AppColors.rechazo)),
            ],
          ],
        );
      },
    );
  }
}

/// Deja solo dígitos y como máximo [longitud]. Si se pega un texto con el
/// código completo ("123456", "123 456", "Tu código: 123-456"), ese código
/// reemplaza lo que hubiera en lugar de sumarse a los dígitos ya escritos.
class FormatoCodigo extends TextInputFormatter {
  FormatoCodigo(this.longitud);

  final int longitud;

  static String _digitos(String texto) => texto.replaceAll(RegExp(r'\D'), '');

  @override
  TextEditingValue formatEditUpdate(TextEditingValue anterior, TextEditingValue nuevo) {
    var digitos = _digitos(nuevo.text);
    if (digitos.length > longitud) {
      final insertados = _digitos(_textoInsertado(anterior, nuevo));
      digitos = insertados.length >= longitud
          ? insertados.substring(0, longitud)
          : digitos.substring(0, longitud);
    }
    return TextEditingValue(
      text: digitos,
      selection: TextSelection.collapsed(offset: digitos.length),
    );
  }

  /// Lo que la edición agregó: el texto nuevo sin el prefijo y el sufijo que
  /// ya estaban en el anterior.
  static String _textoInsertado(TextEditingValue anterior, TextEditingValue nuevo) {
    final a = anterior.text;
    final n = nuevo.text;
    var inicio = 0;
    while (inicio < a.length && inicio < n.length && a[inicio] == n[inicio]) {
      inicio++;
    }
    var finA = a.length;
    var finN = n.length;
    while (finA > inicio && finN > inicio && a[finA - 1] == n[finN - 1]) {
      finA--;
      finN--;
    }
    return n.substring(inicio, finN);
  }
}

class _Cuadro extends StatelessWidget {
  const _Cuadro({
    required this.digito,
    required this.activo,
    required this.conError,
    required this.habilitado,
  });

  final String digito;
  final bool activo;
  final bool conError;
  final bool habilitado;

  @override
  Widget build(BuildContext context) {
    final Color borde;
    if (conError) {
      borde = AppColors.rechazo;
    } else if (activo) {
      borde = AppColors.marca;
    } else if (digito.isNotEmpty) {
      borde = AppColors.tintaSuave;
    } else {
      borde = AppColors.linea;
    }
    return AnimatedContainer(
      duration: const Duration(milliseconds: 120),
      constraints: const BoxConstraints(maxWidth: 56),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: habilitado ? AppColors.superficie : AppColors.lineaSuave,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: borde, width: activo || conError ? 2 : 1),
      ),
      child: Text(
        digito,
        style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: AppColors.tinta,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}
