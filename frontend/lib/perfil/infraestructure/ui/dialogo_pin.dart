import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/ui/widgets_estado.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/format/formatters.dart';
import '../../../pagos/domain/validadores_compra.dart';

/// Cambio de PIN de una cuenta. El servidor guarda solo un hash del PIN, así
/// que no se puede consultar: aquí se escribe el actual y el nuevo. Cada campo
/// va oculto y tiene su ojito para ver lo que se está escribiendo.
/// [alGuardar] devuelve el mensaje de error, o null si se guardó.
class DialogoCambiarPin extends StatefulWidget {
  const DialogoCambiarPin({super.key, required this.numeroCuenta, required this.alGuardar});

  final String numeroCuenta;
  final Future<String?> Function(String? pinActual, String pinNuevo) alGuardar;

  @override
  State<DialogoCambiarPin> createState() => _DialogoCambiarPinState();
}

class _DialogoCambiarPinState extends State<DialogoCambiarPin> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nuevo = TextEditingController();
  final _confirmar = TextEditingController();
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _actual.dispose();
    _nuevo.dispose();
    _confirmar.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _enviando = true;
      _error = null;
    });
    final error = await widget.alGuardar(
      _actual.text.isEmpty ? null : _actual.text,
      _nuevo.text,
    );
    if (!mounted) return;
    if (error == null) {
      Navigator.of(context).pop(true);
    } else {
      setState(() {
        _enviando = false;
        _error = error;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Cambiar PIN'),
      content: SizedBox(
        width: 400,
        child: SingleChildScrollView(
          child: Form(
            key: _form,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Cuenta ${cuentaEnmascarada(widget.numeroCuenta)}',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.tintaSuave,
                  ),
                ),
                const SizedBox(height: 16),
                _CampoPin(
                  controller: _actual,
                  etiqueta: 'PIN actual',
                  ayuda: 'Déjalo vacío solo si aún no tienes PIN',
                  enabled: !_enviando,
                  validar: (v) => (v == null || v.isEmpty) ? null : ValidadoresCompra.pin(v),
                ),
                const SizedBox(height: 16),
                _CampoPin(
                  controller: _nuevo,
                  etiqueta: 'PIN nuevo',
                  ayuda: 'De 4 a 6 dígitos',
                  enabled: !_enviando,
                  validar: ValidadoresCompra.pin,
                ),
                const SizedBox(height: 16),
                _CampoPin(
                  controller: _confirmar,
                  etiqueta: 'Confirmar PIN nuevo',
                  enabled: !_enviando,
                  validar: (v) => v == _nuevo.text ? null : 'Los PIN no coinciden.',
                ),
                if (_error != null) ...[
                  const SizedBox(height: 16),
                  AvisoEstado(tono: Tono.rechazo, icono: Icons.error_outline, texto: _error!),
                ],
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _enviando ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: _enviando ? null : _guardar,
          child: _enviando
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Text('Guardar PIN'),
        ),
      ],
    );
  }
}

/// Campo de PIN oculto como una contraseña, con ojito para mostrarlo.
class _CampoPin extends StatefulWidget {
  const _CampoPin({
    required this.controller,
    required this.etiqueta,
    required this.validar,
    required this.enabled,
    this.ayuda,
  });

  final TextEditingController controller;
  final String etiqueta;
  final String? ayuda;
  final bool enabled;
  final String? Function(String?) validar;

  @override
  State<_CampoPin> createState() => _CampoPinState();
}

class _CampoPinState extends State<_CampoPin> {
  bool _ocultar = true;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: widget.controller,
      enabled: widget.enabled,
      obscureText: _ocultar,
      keyboardType: TextInputType.number,
      enableSuggestions: false,
      autocorrect: false,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
      validator: widget.validar,
      decoration: InputDecoration(
        labelText: widget.etiqueta,
        helperText: widget.ayuda,
        suffixIcon: IconButton(
          tooltip: _ocultar ? 'Mostrar ${widget.etiqueta}' : 'Ocultar ${widget.etiqueta}',
          icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
          onPressed: () => setState(() => _ocultar = !_ocultar),
        ),
      ),
    );
  }
}
