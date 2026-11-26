import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/catalogos/catalogos.dart';
import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/selector_catalogo.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../../core/validation/texto_seguro.dart';
import '../../../authentication/domain/validadores_registro.dart';
import '../../domain/entities/perfil.dart';

/// Formulario de edición del perfil. Nombre, fecha de nacimiento, sexo,
/// nacionalidad, CURP, RFC y correo se muestran en su lugar pero de solo
/// lectura (el correo es el usuario de acceso).
/// Entrega a [alGuardar] solo los campos editables; el caso de uso calcula
/// qué cambió. [alGuardar] devuelve el mensaje de error, o null si se guardó.
class FormularioPerfil extends StatefulWidget {
  const FormularioPerfil({
    super.key,
    required this.perfil,
    required this.catalogos,
    required this.guardando,
    required this.alGuardar,
    required this.alCancelar,
  });

  final Perfil perfil;
  final Catalogos catalogos;
  final bool guardando;
  final Future<String?> Function(Map<String, Object?> editado) alGuardar;
  final VoidCallback alCancelar;

  @override
  State<FormularioPerfil> createState() => _FormularioPerfilState();
}

class _FormularioPerfilState extends State<FormularioPerfil> {
  final _form = GlobalKey<FormState>();
  late final Map<String, TextEditingController> _c;
  String? _estadoCivil;
  String? _error;

  @override
  void initState() {
    super.initState();
    final p = widget.perfil;
    final d = p.domicilio;
    _c = {
      'telefonoAlternativo': p.telefonoAlternativo ?? '',
      'ocupacion': p.ocupacion ?? '',
      'empresa': p.empresa ?? '',
      'ingresoMensual': p.ingresoMensual?.toStringAsFixed(2) ?? '',
      'calle': d?.calle ?? '',
      'numeroExterior': d?.numeroExterior ?? '',
      'numeroInterior': d?.numeroInterior ?? '',
      'colonia': d?.colonia ?? '',
      'municipio': d?.municipio ?? '',
      'estado': d?.estado ?? '',
      'codigoPostal': d?.codigoPostal ?? '',
      'pais': d?.pais ?? paisPorOmision,
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
    // No es editable, pero la API recibe el perfil completo.
    'telefonoMovil': widget.perfil.telefonoMovil,
    'telefonoAlternativo': _opcional('telefonoAlternativo'),
    // Empleo y domicilio son opcionales: vacíos viajan como null.
    'ocupacion': _opcional('ocupacion'),
    'empresa': _opcional('empresa'),
    'ingresoMensual': _opcional('ingresoMensual') == null
        ? null
        : parseMonto(_t('ingresoMensual')),
    'domicilio': _domicilioOmitido
        ? null
        : {
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

  static const _domicilioCampos = [
    'calle',
    'numeroExterior',
    'numeroInterior',
    'colonia',
    'municipio',
    'estado',
    'codigoPostal',
  ];

  /// Sin domicilio previo y sin capturar nada (el país viene sugerido): se deja vacío.
  bool get _domicilioOmitido =>
      widget.perfil.domicilio == null &&
      _domicilioCampos.every((k) => _t(k).isEmpty);

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
      suffixIcon: const Icon(
        Icons.lock_outline,
        size: 18,
        color: AppColors.tintaSuave,
      ),
    ),
  );

  /// Las mismas reglas del registro ([ValidadoresRegistro]); vacío es válido
  /// en los campos opcionales de empleo y domicilio.
  static String? Function(String?) _siHay(String? Function(String?) regla) =>
      (v) => (v ?? '').trim().isEmpty ? null : regla(v);

  /// Campo de domicilio: obligatorio, salvo que el domicilio se esté omitiendo.
  String? Function(String?) _dom(String? Function(String?) regla) =>
      (v) => _domicilioOmitido ? null : regla(v);

  Widget _campo(
    String clave,
    String etiqueta, {
    required String? Function(String?) validar,
    TextInputType? teclado,
    int? digitos,
    int max = 250,
    bool seguro = true,
  }) {
    final controlador = _c[clave]!;
    // Se vuelve a pintar al escribir: el sombreado desaparece en cuanto el campo tiene datos.
    return ListenableBuilder(
      listenable: controlador,
      builder: (context, _) => TextFormField(
        controller: controlador,
        decoration: InputDecoration(
          labelText: etiqueta,
          fillColor: controlador.text.isEmpty ? _sinDatos : null,
        ),
        validator: validar,
        // Errores en vivo, igual que en el registro.
        autovalidateMode: AutovalidateMode.onUserInteraction,
        keyboardType: digitos != null ? TextInputType.number : teclado,
        inputFormatters: [
          LengthLimitingTextInputFormatter(digitos ?? max),
          if (digitos != null) FilteringTextInputFormatter.digitsOnly,
          if (seguro) TextoSeguro.formato,
        ],
        enabled: !widget.guardando,
      ),
    );
  }

  /// Sombreado suave de un campo editable que aún no tiene datos.
  static final _sinDatos = AppColors.pendienteFondo.withValues(alpha: 0.55);

  @override
  Widget build(BuildContext context) {
    final p = widget.perfil;
    return Form(
      key: _form,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Editar perfil',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 4),
          Text(
            'Solo puedes cambiar tu estado civil, teléfono alternativo, empleo y domicilio. '
            'Empleo y domicilio son opcionales: agrégalos cuando quieras.',
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: AppColors.tintaSuave),
          ),
          const SizedBox(height: 20),
          _Grupo(
            titulo: 'Datos personales',
            campos: [
              _fijo('Primer nombre', p.primerNombre),
              _fijo('Segundo nombre', p.segundoNombre ?? ''),
              _fijo('Apellido paterno', p.apellidoPaterno),
              _fijo('Apellido materno', p.apellidoMaterno),
              _fijo('Fecha de nacimiento', formatoFecha(p.fechaNacimiento)),
              _fijo('Sexo', p.sexo),
              DropdownButtonFormField<String>(
                initialValue: _estadoCivil,
                decoration: InputDecoration(
                  labelText: 'Estado civil (opcional)',
                  fillColor: _estadoCivil == null ? _sinDatos : null,
                ),
                items: [
                  for (final e in estadosCiviles)
                    DropdownMenuItem(value: e, child: Text(e)),
                ],
                onChanged: widget.guardando
                    ? null
                    : (v) => setState(() => _estadoCivil = v),
              ),
              _fijo('Nacionalidad', p.nacionalidad),
              _fijo('CURP', p.curp),
              _fijo('RFC', p.rfc),
            ],
          ),
          _Grupo(
            titulo: 'Contacto',
            campos: [
              _fijo('Correo electrónico', p.correoElectronico),
              _fijo('Teléfono móvil', p.telefonoMovil),
              _campo(
                'telefonoAlternativo',
                'Teléfono alternativo (opcional)',
                digitos: 10,
                validar: (v) => ValidadoresRegistro.telefono(v, opcional: true),
              ),
            ],
          ),
          _Grupo(
            titulo: 'Empleo',
            campos: [
              SelectorCatalogo(
                key: const ValueKey('perfil-ocupacion'),
                controlador: _c['ocupacion']!,
                etiqueta: 'Ocupación (opcional)',
                opciones: widget.catalogos.ocupaciones,
                habilitado: !widget.guardando,
                opcional: true,
              ),
              _campo(
                'empresa',
                'Empresa (opcional)',
                validar: _siHay(
                  (v) => ValidadoresRegistro.requerido(v, max: 50),
                ),
                max: 50,
              ),
              _campo(
                'ingresoMensual',
                'Ingreso mensual (opcional)',
                teclado: const TextInputType.numberWithOptions(decimal: true),
                validar: (v) => ValidadoresRegistro.ingreso(v, opcional: true),
                max: 17,
                seguro: false, // lleva punto decimal; `ingreso` rechaza todo lo no numérico
              ),
            ],
          ),
          _Grupo(
            titulo: 'Domicilio',
            campos: [
              _campo(
                'calle',
                'Calle',
                validar: _dom(
                  (v) => ValidadoresRegistro.requerido(v, min: 3, max: 100),
                ),
                max: 100,
              ),
              _campo(
                'numeroExterior',
                'Número exterior',
                validar: _dom((v) => ValidadoresRegistro.requerido(v, max: 50)),
                max: 50,
              ),
              _campo(
                'numeroInterior',
                'Número interior (opcional)',
                validar: TextoSeguro.validar,
                max: 50,
              ),
              _campo(
                'colonia',
                'Colonia',
                validar: _dom(
                  (v) => ValidadoresRegistro.requerido(v, min: 3, max: 100),
                ),
                max: 100,
              ),
              _campo(
                'codigoPostal',
                'Código postal',
                digitos: 5,
                validar: _dom(ValidadoresRegistro.codigoPostal),
              ),
              _campo(
                'municipio',
                'Municipio o alcaldía',
                validar: _dom(ValidadoresRegistro.requerido),
              ),
              _campo(
                'estado',
                'Estado',
                validar: _dom(ValidadoresRegistro.requerido),
              ),
              SelectorCatalogo(
                key: const ValueKey('perfil-pais'),
                controlador: _c['pais']!,
                etiqueta: 'País',
                opciones: widget.catalogos.paises,
                habilitado: !widget.guardando,
                validar: _dom(
                  (v) => v == null ? 'Selecciona una opción.' : null,
                ),
              ),
            ],
          ),
          if (_error != null) ...[
            AvisoEstado(
              tono: Tono.rechazo,
              icono: Icons.error_outline,
              texto: _error!,
            ),
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
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
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
                children: [
                  for (final c in campos) SizedBox(width: ancho, child: c),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}
