import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../authentication/domain/validadores_credenciales.dart';
import '../../../authentication/domain/validadores_registro.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/requisito_password.dart';
import '../../../core/ui/ilustracion.dart';

/// Pide la contraseña actual y la nueva (con confirmación) bajo las mismas reglas
/// que el registro. [alConfirmar] devuelve el mensaje de error, o null si se
/// cambió; entonces el diálogo se cierra con `true`.
class DialogoPassword extends StatefulWidget {
  const DialogoPassword({super.key, required this.alConfirmar});
  final Future<String?> Function(String actual, String nueva) alConfirmar;

  @override
  State<DialogoPassword> createState() => _DialogoPasswordState();
}

class _DialogoPasswordState extends State<DialogoPassword> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  final _confirmacion = TextEditingController();
  // Cada campo oculta o muestra su contenido por separado.
  bool _verActual = false;
  bool _verNueva = false;
  bool _verConfirmacion = false;
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _actual.dispose();
    _nueva.dispose();
    _confirmacion.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    if (_enviando || !_form.currentState!.validate()) return;
    setState(() {
      _enviando = true;
      _error = null;
    });
    final error = await widget.alConfirmar(_actual.text, _nueva.text);
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

  Widget _campo({
    required Key llave,
    required TextEditingController controller,
    required String etiqueta,
    required IconData icono,
    required bool visible,
    required VoidCallback alternar,
    required String? Function(String?) validator,
    bool nueva = false,
    ValueChanged<String>? alCambiar,
    ValueChanged<String>? alEnviar,
  }) => TextFormField(
    key: llave,
    controller: controller,
    obscureText: !visible,
    readOnly: _enviando,
    autofillHints: [nueva ? AutofillHints.newPassword : AutofillHints.password],
    enableSuggestions: false,
    autocorrect: false,
    inputFormatters: nueva ? [LengthLimitingTextInputFormatter(12)] : null,
    autovalidateMode: nueva ? AutovalidateMode.onUserInteraction : null,
    onChanged: alCambiar,
    onFieldSubmitted: alEnviar,
    decoration: InputDecoration(
      labelText: etiqueta,
      prefixIcon: Icon(icono, size: 20, color: AppColors.marca),
      suffixIcon: IconButton(
        tooltip: visible ? 'Ocultar $etiqueta' : 'Mostrar $etiqueta',
        icon: Icon(
          visible ? Icons.visibility_off_outlined : Icons.visibility_outlined,
          size: 20,
        ),
        onPressed: alternar,
      ),
    ),
    validator: validator,
  );

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return AlertDialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      contentPadding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
      title: Row(
        children: [
          const IconoEnRecuadro(
            icono: Icons.lock_reset,
            color: AppColors.marca,
            fondo: AppColors.turquesaTenue,
            tamano: 48,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Cambiar contraseña'),
                const SizedBox(height: 2),
                Text(
                  'Elige una clave nueva y segura.',
                  style: tema.bodySmall?.copyWith(color: AppColors.tintaSuave),
                ),
              ],
            ),
          ),
        ],
      ),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Form(
            key: _form,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _campo(
                  llave: const ValueKey('pw-actual'),
                  controller: _actual,
                  etiqueta: 'Contraseña actual',
                  icono: Icons.lock_outline,
                  visible: _verActual,
                  alternar: () => setState(() => _verActual = !_verActual),
                  validator: ValidadoresCredenciales.password,
                ),
                const SizedBox(height: 18),
                _campo(
                  llave: const ValueKey('pw-nueva'),
                  controller: _nueva,
                  etiqueta: 'Nueva contraseña',
                  icono: Icons.key_outlined,
                  visible: _verNueva,
                  alternar: () => setState(() => _verNueva = !_verNueva),
                  validator: ValidadoresRegistro.password,
                  nueva: true,
                  // Reconstruye para que la confirmación se revalide con la nueva contraseña.
                  alCambiar: (_) => setState(() {}),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.papel,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppColors.lineaSuave),
                  ),
                  child: Wrap(
                    spacing: 12,
                    runSpacing: 6,
                    children: [
                      for (final (texto, cumple)
                          in ValidadoresRegistro.requisitosPassword(_nueva.text))
                        RequisitoPassword(texto: texto, cumple: cumple),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                _campo(
                  llave: const ValueKey('pw-confirmacion'),
                  controller: _confirmacion,
                  etiqueta: 'Confirmar contraseña',
                  icono: Icons.verified_user_outlined,
                  visible: _verConfirmacion,
                  alternar: () => setState(() => _verConfirmacion = !_verConfirmacion),
                  validator: (v) => ValidadoresRegistro.confirmacion(v, _nueva.text),
                  nueva: true,
                  alEnviar: (_) => _enviar(),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 14),
                  Container(
                    key: const ValueKey('pw-error'),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.coralTenue,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.error_outline, size: 18, color: AppColors.coral),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _error!,
                            style: tema.bodyMedium?.copyWith(color: AppColors.coral),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
      actionsPadding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
      actions: [
        TextButton(
          onPressed: _enviando ? null : () => Navigator.of(context).pop(false),
          child: const Text('Cancelar'),
        ),
        FilledButton.icon(
          onPressed: _enviando ? null : _enviar,
          icon: _enviando
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Icon(Icons.check, size: 18),
          label: Text(_enviando ? 'Guardando…' : 'Cambiar contraseña'),
        ),
      ],
    );
  }
}
