import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../domain/entities/perfil.dart';

/// Formulario de edición del perfil. Nombre, fecha de nacimiento, sexo,
/// nacionalidad, CURP, RFC y correo se muestran en su lugar pero de solo
/// lectura (el correo es el usuario de acceso y destino del código 2FA).
/// Entrega a [alGuardar] solo los campos editables; el caso de uso calcula
/// qué cambió. [alGuardar] devuelve el mensaje de error, o null si se guardó.
class FormularioPerfil extends StatefulWidget {
  const FormularioPerfil({
    super.key,
    required this.perfil,
    required this.guardando,
    required this.alGuardar,
    required this.alCancelar,
  });

  final Perfil perfil;
  final bool guardando;
  final Future<String?> Function(Map<String, Object?> editado) alGuardar;
  final VoidCallback alCancelar;

  @override
  State<FormularioPerfil> createState() => _FormularioPerfilState();
}

class _FormularioPerfilState extends State<FormularioPerfil> {
  final _form = GlobalKey<FormState>();
  late final Map<String, TextEditingController> _c;
  late String _estadoCivil;
  String? _error;

  @override
  void initState() {
    super.initState();
    final p = widget.perfil;
    final d = p.domicilio;
    _c = {
      'telefonoMovil': p.telefonoMovil,
      'telefonoAlternativo': p.telefonoAlternativo ?? '',
      'ocupacion': p.ocupacion,
      'empresa': p.empresa,
      'ingresoMensual': p.ingresoMensual.toStringAsFixed(2),
      'calle': d.calle,
      'numeroExterior': d.numeroExterior,
      'numeroInterior': d.numeroInterior ?? '',
      'colonia': d.colonia,
      'municipio': d.municipio,
      'estado': d.estado,
      'codigoPostal': d.codigoPostal,
      'pais': d.pais,
    }.map((k, v) => MapEntry(k, TextEditingController(text: v)));
    _estadoCivil = p.estadoCivil;
  }

  @override
  void dispose() {
    for (final c in _c.values) {
      c.dispose();
    }
    super.dispose();
  }

  String _t(String k) => _c[k]!.text.trim();
  String? _opcional(String k) => _t(k).isEmpty ? null : _t(k);

  Map<String, Object?> _editado() => {
    'estadoCivil': _estadoCivil,
    'telefonoMovil': _t('telefonoMovil'),
    'telefonoAlternativo': _opcional('telefonoAlternativo'),
    'ocupacion': _t('ocupacion'),
    'empresa': _t('empresa'),
    'ingresoMensual': parseMonto(_t('ingresoMensual')),
    'domicilio': {
      'calle': _t('calle'),
      'numeroExterior': _t('numeroExterior'),
      'numeroInterior': _opcional('numeroInterior'),
      'colonia': _t('colonia'),
      'municipio': _t('municipio'),
      'estado': _t('estado'),
      'codigoPostal': _t('codigoPostal'),
      'pais': _t('pais'),
    },
  };

  Future<void> _guardar() async {
    setState(() => _error = null);
    if (!_form.currentState!.validate()) return;
    final error = await widget.alGuardar(_editado());
    if (mounted && error != null) setState(() => _error = error);
  }

  /// Campo en su lugar pero inerte: sin foco ni edición. Fondo gris y texto
  /// legible (no el gris de "deshabilitado") con candado para distinguirlo.
  Widget _fijo(String etiqueta, String valor) => TextFormField(
    initialValue: valor,
    enabled: false,
    style: const TextStyle(color: AppColors.tinta),
    decoration: InputDecoration(
      labelText: etiqueta,
      labelStyle: const TextStyle(color: AppColors.tintaSuave),
      floatingLabelStyle: const TextStyle(color: AppColors.tintaSuave),
      fillColor: AppColors.lineaSuave,
      suffixIcon: const Icon(Icons.lock_outline, size: 18, color: AppColors.tintaSuave),
    ),
  );

  static String? _requerido(String? v) =>
      (v == null || v.trim().isEmpty) ? 'Este campo es obligatorio.' : null;

  static String? Function(String?) _digitos(int n, {bool opcional = false}) => (v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return opcional ? null : 'Este campo es obligatorio.';
    return RegExp('^\\d{$n}\$').hasMatch(t) ? null : 'Debe tener $n dígitos.';
  };

  Widget _campo(
    String clave,
    String etiqueta, {
    String? Function(String?)? validar = _requerido,
    TextInputType? teclado,
    int? digitos,
  }) {
    return TextFormField(
      controller: _c[clave],
      decoration: InputDecoration(labelText: etiqueta),
      validator: validar,
      keyboardType: digitos != null ? TextInputType.number : teclado,
      inputFormatters: digitos != null
          ? [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(digitos)]
          : null,
      enabled: !widget.guardando,
    );
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.perfil;
    return Form(
      key: _form,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Editar perfil', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 4),
          Text(
            'Solo puedes cambiar tu estado civil, teléfonos, empleo y domicilio.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.tintaSuave),
          ),
          const SizedBox(height: 20),
          _Grupo(titulo: 'Datos personales', campos: [
            _fijo('Primer nombre', p.primerNombre),
            _fijo('Segundo nombre', p.segundoNombre ?? ''),
            _fijo('Apellido paterno', p.apellidoPaterno),
            _fijo('Apellido materno', p.apellidoMaterno),
            _fijo('Fecha de nacimiento', formatoFecha(p.fechaNacimiento)),
            _fijo('Sexo', p.sexo),
            DropdownButtonFormField<String>(
              initialValue: _estadoCivil,
              decoration: const InputDecoration(labelText: 'Estado civil'),
              items: [
                for (final e in estadosCiviles) DropdownMenuItem(value: e, child: Text(e)),
              ],
              onChanged: widget.guardando ? null : (v) => setState(() => _estadoCivil = v!),
            ),
            _fijo('Nacionalidad', p.nacionalidad),
            _fijo('CURP', p.curp),
            _fijo('RFC', p.rfc),
          ]),
          _Grupo(titulo: 'Contacto', campos: [
            _fijo('Correo electrónico', p.correoElectronico),
            _campo('telefonoMovil', 'Teléfono móvil', digitos: 10, validar: _digitos(10)),
            _campo(
              'telefonoAlternativo',
              'Teléfono alternativo (opcional)',
              digitos: 10,
              validar: _digitos(10, opcional: true),
            ),
          ]),
          _Grupo(titulo: 'Empleo', campos: [
            _campo('ocupacion', 'Ocupación'),
            _campo('empresa', 'Empresa'),
            _campo(
              'ingresoMensual',
              'Ingreso mensual',
              teclado: const TextInputType.numberWithOptions(decimal: true),
              validar: (v) {
                final m = parseMonto(v ?? '');
                return m == null || m <= 0 ? 'Escribe un monto mayor a cero.' : null;
              },
            ),
          ]),
          _Grupo(titulo: 'Domicilio', campos: [
            _campo('calle', 'Calle'),
            _campo('numeroExterior', 'Número exterior'),
            _campo('numeroInterior', 'Número interior (opcional)', validar: null),
            _campo('colonia', 'Colonia'),
            _campo('codigoPostal', 'Código postal', digitos: 5, validar: _digitos(5)),
            _campo('municipio', 'Municipio o alcaldía'),
            _campo('estado', 'Estado'),
            _campo('pais', 'País'),
          ]),
          if (_error != null) ...[
            AvisoEstado(tono: Tono.rechazo, icono: Icons.error_outline, texto: _error!),
            const SizedBox(height: 16),
          ],
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              TextButton(
                onPressed: widget.guardando ? null : widget.alCancelar,
                child: const Text('Cancelar'),
              ),
              const SizedBox(width: 12),
              FilledButton(
                onPressed: widget.guardando ? null : _guardar,
                child: widget.guardando
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('Guardar cambios'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Grupo de campos con título, en dos columnas cuando hay espacio.
class _Grupo extends StatelessWidget {
  const _Grupo({required this.titulo, required this.campos});
  final String titulo;
  final List<Widget> campos;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.superficie,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.lineaSuave),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titulo, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 16),
          LayoutBuilder(
            builder: (context, r) {
              final columnas = r.maxWidth >= 560 ? 2 : 1;
              final ancho = (r.maxWidth - 16 * (columnas - 1)) / columnas;
              return Wrap(
                spacing: 16,
                runSpacing: 16,
                children: [for (final c in campos) SizedBox(width: ancho, child: c)],
              );
            },
          ),
        ],
      ),
    );
  }
}
