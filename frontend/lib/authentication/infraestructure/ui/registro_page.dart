import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/marco_acceso.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../../perfil/domain/entities/perfil.dart' show estadosCiviles;
import '../../domain/ports/registro_gateway.dart';
import '../../domain/validadores_registro.dart';

typedef _Paso = ({
  String titulo,
  String subtitulo,
  String etiqueta,
  IconData icono,
  Color color,
  Color tenue,
  bool opcional,
});

const List<_Paso> _pasos = [
  (
    titulo: 'Tu nombre',
    subtitulo: '¿Cómo te llamas y cuándo naciste?',
    etiqueta: 'Nombre',
    icono: Icons.person_outline,
    color: AppColors.turquesa,
    tenue: AppColors.turquesaTenue,
    opcional: false,
  ),
  (
    titulo: 'Tu identidad',
    subtitulo: 'Para verificarte y poder contactarte.',
    etiqueta: 'Datos',
    icono: Icons.badge_outlined,
    color: AppColors.indigo,
    tenue: AppColors.indigoTenue,
    opcional: false,
  ),
  (
    titulo: 'Tu empresa',
    subtitulo: 'Opcional: dónde trabajas y cuánto ingresas.',
    etiqueta: 'Empresa',
    icono: Icons.business_center_outlined,
    color: AppColors.azul,
    tenue: AppColors.azulTenue,
    opcional: true,
  ),
  (
    titulo: 'Tu domicilio',
    subtitulo: 'Opcional: dónde vives actualmente.',
    etiqueta: 'Domicilio',
    icono: Icons.home_outlined,
    color: AppColors.coral,
    tenue: AppColors.coralTenue,
    opcional: true,
  ),
  (
    titulo: 'Acceso a tu cuenta',
    subtitulo: 'Con esto iniciarás sesión.',
    etiqueta: 'Acceso',
    icono: Icons.lock_outline,
    color: AppColors.ciruela,
    tenue: AppColors.ciruelaTenue,
    opcional: false,
  ),
];

/// Pantalla para crear una cuenta (adaptador de entrada), en cinco pasos:
/// nombre, identidad, empresa, domicilio y acceso. Empresa y domicilio son opcionales (se pueden omitir y
/// completar después en "Editar perfil"). Cada paso cabe en pantalla. El servidor crea además la
/// cuenta bancaria del usuario, ligada a él y con saldo cero.
class RegistroPage extends StatefulWidget {
  const RegistroPage({
    super.key,
    required this.registro,
    required this.alVolver,
  });

  final RegistroGateway registro;

  /// Regresa al login (desde el enlace o al terminar el registro).
  final VoidCallback alVolver;

  @override
  State<RegistroPage> createState() => _RegistroPageState();
}

class _RegistroPageState extends State<RegistroPage> {
  static const _campos = [
    'primerNombre',
    'segundoNombre',
    'apellidoPaterno',
    'apellidoMaterno',
    'fecha',
    'nacionalidad',
    'curp',
    'rfc',
    'telefonoMovil',
    'telefonoAlternativo',
    'ocupacion',
    'empresa',
    'ingresoMensual',
    'calle',
    'numeroExterior',
    'numeroInterior',
    'colonia',
    'municipio',
    'estado',
    'codigoPostal',
    'pais',
    'correo',
    'password',
    'confirmacion',
  ];

  /// Paso "Datos" (CURP, RFC y teléfono): se comprueba aquí que no estén repetidos.
  static const _pasoDatos = 1;

  /// Pasos opcionales y los campos que se vacían al omitirlos.
  static const _pasoEmpresa = 2;
  static const _pasoDomicilio = 3;
  static const _camposEmpresa = ['empresa', 'ocupacion', 'ingresoMensual'];
  static const _camposDomicilio = [
    'calle',
    'numeroExterior',
    'numeroInterior',
    'colonia',
    'municipio',
    'estado',
    'codigoPostal',
  ];

  /// Campo de la API -> campo del formulario y aviso cuando ya tiene cuenta.
  static const _repetidoEn = {
    'curp': ('curp', 'Esta CURP ya está registrada.'),
    'rfc': ('rfc', 'Este RFC ya está registrado.'),
    'telefonoMovil': ('telefonoMovil', 'Este teléfono ya está registrado.'),
    'correoElectronico': (
      'correo',
      'Este correo ya está registrado. Inicia sesión o usa otro.',
    ),
  };

  /// Valor (normalizado) que el servidor dijo que ya existe, por campo. El aviso
  /// desaparece solo en cuanto el usuario cambia el valor.
  final _repetidos = <String, String>{};

  final _claves = List.generate(_pasos.length, (_) => GlobalKey<FormState>());
  final _c = {for (final k in _campos) k: TextEditingController()};
  int _paso = 0;
  DateTime? _fecha;
  String? _sexo;
  String? _estadoCivil;
  bool _ocultar = true;
  bool _ocultarConfirmacion = true;
  bool _enviando = false;
  bool _creada = false;
  double _altoMinimo = 0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _c['nacionalidad']!.text = 'Mexicana';
    _c['pais']!.text = 'México';
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

  Map<String, Object?> _datos() {
    final f = _fecha!;
    return {
      'primerNombre': _t('primerNombre'),
      // Vacío no es válido para el servidor (mínimo 2 letras): se omite.
      'segundoNombre': _opcional('segundoNombre'),
      'apellidoPaterno': _t('apellidoPaterno'),
      'apellidoMaterno': _t('apellidoMaterno'),
      'fechaNacimiento':
          '${f.year.toString().padLeft(4, '0')}-${f.month.toString().padLeft(2, '0')}-${f.day.toString().padLeft(2, '0')}',
      'curp': _t('curp').toUpperCase(),
      'rfc': _t('rfc').toUpperCase(),
      'sexo': _sexo,
      'nacionalidad': _t('nacionalidad'),
      'estadoCivil': _estadoCivil,
      'correoElectronico': _t('correo'),
      'telefonoMovil': _t('telefonoMovil'),
      'telefonoAlternativo': _opcional('telefonoAlternativo'),
      // Empresa y domicilio se pueden omitir: vacíos viajan como null.
      'ocupacion': _opcional('ocupacion'),
      'empresa': _opcional('empresa'),
      'ingresoMensual': _opcional('ingresoMensual') == null
          ? null
          : parseMonto(_t('ingresoMensual')),
      'domicilio': _opcional('calle') == null
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
      'password': _c['password']!.text,
    };
  }

  String _normal(String? v) => (v ?? '').trim().toLowerCase();

  String? _avisoRepetido(String nombre, String? valor) {
    for (final (campo, aviso) in _repetidoEn.values) {
      if (campo == nombre && _repetidos[nombre] == _normal(valor)) return aviso;
    }
    return null;
  }

  /// Datos únicos que se capturaron en el paso actual y hay que comprobar.
  Map<String, String> _porComprobar() => switch (_paso) {
    _pasoDatos => {
      'curp': _t('curp').toUpperCase(),
      'rfc': _t('rfc').toUpperCase(),
      'telefonoMovil': _t('telefonoMovil'),
    },
    final p when p == _pasos.length - 1 => {'correoElectronico': _t('correo')},
    _ => const {},
  };

  Future<void> _siguiente() async {
    if (_enviando || !(_claves[_paso].currentState?.validate() ?? false)) {
      return;
    }
    final comprobar = _porComprobar();
    final ultimo = _paso == _pasos.length - 1;
    if (comprobar.isEmpty && !ultimo) {
      setState(() {
        _paso++;
        _error = null;
      });
      return;
    }
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final repetidos = comprobar.isEmpty
          ? const <String>[]
          : await widget.registro.repetidos(comprobar);
      if (repetidos.isNotEmpty) {
        // Se marca cada campo repetido y el usuario se queda en este paso.
        for (final nombre in repetidos) {
          final campo = _repetidoEn[nombre]?.$1;
          if (campo != null) _repetidos[campo] = _normal(comprobar[nombre]);
        }
        if (mounted) {
          setState(() => _enviando = false);
          _claves[_paso].currentState?.validate();
        }
        return;
      }
      if (!ultimo) {
        if (mounted) setState(() => _paso++);
        return;
      }
      await widget.registro.registrar(_datos());
      if (mounted) setState(() => _creada = true);
    } on Object catch (e) {
      if (mounted) {
        setState(
          () => _error = e is AppException
              ? e.message
              : 'No fue posible crear tu cuenta. Inténtalo de nuevo.',
        );
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  /// Salta un paso opcional: se vacían sus campos para que no viaje nada de él.
  void _omitir() {
    final campos = _paso == _pasoEmpresa ? _camposEmpresa : _camposDomicilio;
    for (final k in campos) {
      _c[k]!.clear();
    }
    if (_paso == _pasoDomicilio) _c['pais']!.text = 'México';
    _claves[_paso].currentState?.reset();
    setState(() {
      _paso++;
      _error = null;
    });
  }

  void _atras() => setState(() {
    _paso--;
    _error = null;
  });

  Future<void> _elegirFecha() async {
    final limite = ValidadoresRegistro.fechaMaximaNacimiento(DateTime.now());
    final elegida = await showDatePicker(
      context: context,
      initialDate: _fecha ?? limite,
      firstDate: DateTime(1900),
      lastDate: limite,
      helpText: 'Fecha de nacimiento',
    );
    if (elegida != null) {
      setState(() => _fecha = elegida);
      _c['fecha']!.text = formatoFecha(elegida);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    _altoMinimo = MarcoAcceso.altoMinimo(context);
    return MarcoAcceso(
      lema: 'Crea tu cuenta en minutos',
      tarjeta: _creada
          ? _Exito(alIniciarSesion: widget.alVolver)
          : _formulario(tema),
      pie: _creada
          ? null
          : TextButton(
              onPressed: _enviando ? null : widget.alVolver,
              style: TextButton.styleFrom(
                foregroundColor: Colors.white,
                minimumSize: const Size(0, 36),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('¿Ya tienes cuenta? Inicia sesión'),
            ),
    );
  }

  /// Separación entre filas de campos: crece con el alto de la tarjeta.
  double get _gap => (12 + (_altoMinimo - 400) / 14).clamp(12.0, 16.0);

  Widget _formulario(TextTheme tema) {
    final p = _pasos[_paso];
    final ultimo = _paso == _pasos.length - 1;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _Progreso(actual: _paso),
            const SizedBox(height: 14),
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: p.tenue,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(p.icono, color: p.color, size: 22),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(p.titulo, style: tema.titleLarge),
                      Text(
                        p.subtitulo,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: tema.bodySmall?.copyWith(
                          color: AppColors.tintaSuave,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  'Paso ${_paso + 1} de ${_pasos.length}',
                  style: tema.bodySmall?.copyWith(
                    color: p.color,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ],
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 14),
            // Ranura fija (siempre presente) para que el aviso no desplace ni recree los campos.
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 200),
              child: _error == null
                  ? const SizedBox.shrink(key: ValueKey('sin-error'))
                  : Padding(
                      key: const ValueKey('con-error'),
                      padding: const EdgeInsets.only(bottom: 12),
                      child: AvisoEstado(
                        tono: Tono.rechazo,
                        icono: Icons.error_outline,
                        texto: _error!,
                      ),
                    ),
            ),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 250),
              transitionBuilder: (hijo, animacion) => FadeTransition(
                opacity: animacion,
                child: SlideTransition(
                  position: Tween(
                    begin: const Offset(0.04, 0),
                    end: Offset.zero,
                  ).animate(animacion),
                  child: hijo,
                ),
              ),
              child: KeyedSubtree(
                key: ValueKey(_paso),
                child: Form(
                  key: _claves[_paso],
                  child: switch (_paso) {
                    0 => _nombre(),
                    1 => _identidad(),
                    2 => _empresa(),
                    3 => _domicilio(),
                    _ => _acceso(),
                  },
                ),
              ),
            ),
          ],
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 16),
            Row(
              children: [
                if (_paso > 0) ...[
                  OutlinedButton.icon(
                    onPressed: _enviando ? null : _atras,
                    icon: const Icon(Icons.arrow_back, size: 18),
                    label: const Text('Atrás'),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: FilledButton(
                    onPressed: _enviando ? null : _siguiente,
                    child: _enviando
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2.5,
                              color: AppColors.tintaSuave,
                            ),
                          )
                        : Text(ultimo ? 'Crear mi cuenta' : 'Continuar'),
                  ),
                ),
                if (p.opcional) ...[
                  const SizedBox(width: 8),
                  TextButton(
                    onPressed: _enviando ? null : _omitir,
                    child: const Text('Omitir'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ],
    );
  }

  // --- Pasos ---------------------------------------------------------------

  Widget _nombre() => _Columna(gap: _gap, [
    _fila([
      _campo(
        'primerNombre',
        'Primer nombre',
        validator: (v) => ValidadoresRegistro.nombre(v, min: 3),
        max: 50,
        capitalizar: TextCapitalization.words,
        hints: [AutofillHints.givenName],
      ),
      _campo(
        'segundoNombre',
        'Segundo nombre (opcional)',
        validator: (v) =>
            ValidadoresRegistro.nombre(v, opcional: true, min: 3),
        max: 50,
        capitalizar: TextCapitalization.words,
      ),
    ]),
    _fila([
      _campo(
        'apellidoPaterno',
        'Apellido paterno',
        validator: ValidadoresRegistro.nombre,
        max: 50,
        capitalizar: TextCapitalization.words,
        hints: [AutofillHints.familyName],
      ),
      _campo(
        'apellidoMaterno',
        'Apellido materno',
        validator: ValidadoresRegistro.nombre,
        max: 50,
        capitalizar: TextCapitalization.words,
      ),
    ]),
    _fila([
      TextFormField(
        key: const ValueKey('reg-fecha'),
        controller: _c['fecha'],
        readOnly: true,
        onTap: _elegirFecha,
        decoration: const InputDecoration(
          labelText: 'Fecha de nacimiento',
          suffixIcon: Icon(Icons.calendar_today_outlined, size: 18),
        ),
        validator: (_) => ValidadoresRegistro.fechaNacimiento(_fecha),
      ),
      _desplegable('Sexo', _sexo, const [
        'Masculino',
        'Femenino',
      ], (v) => setState(() => _sexo = v)),
    ]),
  ]);

  Widget _identidad() => _Columna(gap: _gap, [
    _fila([
      _desplegable(
        'Estado civil',
        _estadoCivil,
        estadosCiviles,
        (v) => setState(() => _estadoCivil = v),
        opcional: true,
      ),
      _campo(
        'nacionalidad',
        'Nacionalidad',
        validator: (v) => ValidadoresRegistro.requerido(v, max: 100),
        max: 100,
      ),
    ]),
    _fila([
      _campo(
        'curp',
        'CURP',
        validator: ValidadoresRegistro.curp,
        max: 18,
        mayusculas: true,
        unico: true,
      ),
      _campo(
        'rfc',
        'RFC',
        validator: ValidadoresRegistro.rfc,
        max: 13,
        mayusculas: true,
        unico: true,
      ),
    ]),
    _fila([
      _campo(
        'telefonoMovil',
        'Teléfono móvil',
        validator: ValidadoresRegistro.telefono,
        max: 10,
        teclado: TextInputType.phone,
        soloDigitos: true,
        unico: true,
        hints: [AutofillHints.telephoneNumberNational],
      ),
      _campo(
        'telefonoAlternativo',
        'Teléfono alternativo (opcional)',
        validator: (v) => ValidadoresRegistro.telefono(v, opcional: true),
        max: 10,
        teclado: TextInputType.phone,
        soloDigitos: true,
      ),
    ]),
  ]);

  Widget _empresa() => _Columna(gap: _gap, [
    _campo(
      'empresa',
      'Empresa donde trabajas',
      validator: (v) => ValidadoresRegistro.requerido(v, max: 50),
      max: 50,
      capitalizar: TextCapitalization.words,
      hints: [AutofillHints.organizationName],
    ),
    _fila([
      _campo(
        'ocupacion',
        'Ocupación o puesto',
        validator: (v) => ValidadoresRegistro.requerido(v, max: 50),
        max: 50,
        capitalizar: TextCapitalization.sentences,
        hints: [AutofillHints.jobTitle],
      ),
      _campo(
        'ingresoMensual',
        'Ingreso mensual (MXN)',
        validator: ValidadoresRegistro.ingreso,
        max: 17,
        teclado: const TextInputType.numberWithOptions(decimal: true),
        prefijo: r'$ ',
      ),
    ]),
  ]);

  Widget _domicilio() => _Columna(gap: _gap, [
    _fila(
      flex: [4, 2, 3],
      minAncho: 460,
      [
        _campo(
          'calle',
          'Calle',
          validator: (v) => ValidadoresRegistro.requerido(v, min: 3, max: 100),
          max: 100,
          capitalizar: TextCapitalization.words,
          hints: [AutofillHints.streetAddressLine1],
        ),
        _campo(
          'numeroExterior',
          'Exterior',
          validator: (v) => ValidadoresRegistro.requerido(v, max: 50),
          max: 50,
        ),
        _campo('numeroInterior', 'Interior (opcional)', max: 50),
      ],
    ),
    _fila(
      flex: [3, 2],
      [
        _campo(
          'colonia',
          'Colonia',
          validator: (v) => ValidadoresRegistro.requerido(v, min: 3, max: 100),
          max: 100,
          capitalizar: TextCapitalization.words,
        ),
        _campo(
          'codigoPostal',
          'Código postal',
          validator: ValidadoresRegistro.codigoPostal,
          max: 5,
          teclado: TextInputType.number,
          soloDigitos: true,
          hints: [AutofillHints.postalCode],
        ),
      ],
    ),
    _fila(minAncho: 460, [
      _campo(
        'municipio',
        'Municipio',
        validator: ValidadoresRegistro.requerido,
        max: 250,
        capitalizar: TextCapitalization.words,
      ),
      _campo(
        'estado',
        'Estado',
        validator: ValidadoresRegistro.requerido,
        max: 250,
        capitalizar: TextCapitalization.words,
        hints: [AutofillHints.addressState],
      ),
      _campo(
        'pais',
        'País',
        validator: (v) => ValidadoresRegistro.requerido(v, max: 100),
        max: 100,
        hints: [AutofillHints.countryName],
      ),
    ]),
  ]);

  Widget _acceso() => AutofillGroup(
    child: _Columna(gap: _gap, [
      _campo(
        'correo',
        'Correo electrónico',
        validator: ValidadoresRegistro.correo,
        max: 100,
        unico: true,
        teclado: TextInputType.emailAddress,
        hints: [AutofillHints.email],
      ),
      _fila([
        TextFormField(
          key: const ValueKey('reg-password'),
          controller: _c['password'],
          obscureText: _ocultar,
          autofillHints: const [AutofillHints.newPassword],
          enableSuggestions: false,
          autocorrect: false,
          inputFormatters: [LengthLimitingTextInputFormatter(12)],
          autovalidateMode: AutovalidateMode.onUserInteraction,
          // Reconstruye la página para que la confirmación se revalide con la nueva contraseña.
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: 'Contraseña',
            suffixIcon: IconButton(
              tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
              icon: Icon(
                _ocultar
                    ? Icons.visibility_outlined
                    : Icons.visibility_off_outlined,
                size: 20,
              ),
              onPressed: () => setState(() => _ocultar = !_ocultar),
            ),
          ),
          validator: ValidadoresRegistro.password,
        ),
        TextFormField(
          key: const ValueKey('reg-confirmacion'),
          controller: _c['confirmacion'],
          obscureText: _ocultarConfirmacion,
          autofillHints: const [AutofillHints.newPassword],
          enableSuggestions: false,
          autocorrect: false,
          inputFormatters: [LengthLimitingTextInputFormatter(12)],
          autovalidateMode: AutovalidateMode.onUserInteraction,
          decoration: InputDecoration(
            labelText: 'Confirmar contraseña',
            suffixIcon: IconButton(
              tooltip: _ocultarConfirmacion
                  ? 'Mostrar confirmación'
                  : 'Ocultar confirmación',
              icon: Icon(
                _ocultarConfirmacion
                    ? Icons.visibility_outlined
                    : Icons.visibility_off_outlined,
                size: 20,
              ),
              onPressed: () =>
                  setState(() => _ocultarConfirmacion = !_ocultarConfirmacion),
            ),
          ),
          validator: (v) =>
              ValidadoresRegistro.confirmacion(v, _c['password']!.text),
          onFieldSubmitted: (_) => _siguiente(),
        ),
      ]),
      ListenableBuilder(
        listenable: _c['password']!,
        builder: (context, _) => Wrap(
          spacing: 10,
          runSpacing: 4,
          children: [
            for (final (texto, cumple)
                in ValidadoresRegistro.requisitosPassword(_c['password']!.text))
              _Requisito(texto: texto, cumple: cumple),
          ],
        ),
      ),
      const AvisoEstado(
        tono: Tono.aviso,
        icono: Icons.account_balance_wallet_outlined,
        texto: r'Abriremos también tu cuenta bancaria con saldo de $0.00.',
      ),
    ]),
  );

  // --- Piezas de formulario --------------------------------------------------

  Widget _campo(
    String nombre,
    String etiqueta, {
    String? Function(String?)? validator,
    int max = 250,
    TextInputType? teclado,
    TextCapitalization capitalizar = TextCapitalization.none,
    bool soloDigitos = false,
    bool mayusculas = false,
    bool unico = false,
    List<String> hints = const [],
    String? prefijo,
  }) => TextFormField(
    key: ValueKey('reg-$nombre'),
    controller: _c[nombre],
    readOnly:
        _enviando, // no `enabled`: ver el login (en web se pierde el foco)
    keyboardType: teclado,
    textCapitalization: mayusculas
        ? TextCapitalization.characters
        : capitalizar,
    autofillHints: hints,
    inputFormatters: [
      LengthLimitingTextInputFormatter(max),
      if (soloDigitos) FilteringTextInputFormatter.digitsOnly,
      if (mayusculas)
        TextInputFormatter.withFunction(
          (_, n) => n.copyWith(text: n.text.toUpperCase()),
        ),
    ],
    decoration: InputDecoration(labelText: etiqueta, prefixText: prefijo),
    // Se valida mientras se escribe (sin esperar a "Continuar"); los datos únicos además
    // se revalidan al editarlos para quitar el aviso de "repetido".
    autovalidateMode: AutovalidateMode.onUserInteraction,
    validator: unico
        ? (v) => validator?.call(v) ?? _avisoRepetido(nombre, v)
        : validator,
  );

  Widget _desplegable(
    String etiqueta,
    String? valor,
    List<String> opciones,
    ValueChanged<String?> alCambiar, {
    bool opcional = false,
  }) => DropdownButtonFormField<String>(
    key: ValueKey('reg-$etiqueta'),
    initialValue: valor,
    isExpanded: true,
    decoration: InputDecoration(
      labelText: opcional ? '$etiqueta (opcional)' : etiqueta,
    ),
    items: [
      for (final o in opciones) DropdownMenuItem(value: o, child: Text(o)),
    ],
    onChanged: _enviando ? null : alCambiar,
    validator: (v) => v == null && !opcional ? 'Selecciona una opción.' : null,
  );

  /// Campos lado a lado (con [flex] como pesos) cuando el ancho llega a
  /// [minAncho]; apilados en pantallas angostas.
  Widget _fila(List<Widget> hijos, {List<int>? flex, double minAncho = 440}) =>
      LayoutBuilder(
        builder: (context, c) {
          if (c.maxWidth < minAncho) return _Columna(hijos, gap: _gap);
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var i = 0; i < hijos.length; i++) ...[
                if (i > 0) const SizedBox(width: 12),
                Expanded(flex: flex?[i] ?? 1, child: hijos[i]),
              ],
            ],
          );
        },
      );
}

/// Apila [hijos] con separación uniforme.
class _Columna extends StatelessWidget {
  const _Columna(this.hijos, {this.gap = 12});
  final List<Widget> hijos;
  final double gap;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      for (var i = 0; i < hijos.length; i++) ...[
        if (i > 0) SizedBox(height: gap),
        hijos[i],
      ],
    ],
  );
}

/// Barra de progreso: un círculo por paso unido por una línea. Los pasos
/// hechos muestran palomita; el actual, su ícono con un aro.
class _Progreso extends StatelessWidget {
  const _Progreso({required this.actual});
  final int actual;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    Widget linea(bool activa) => Expanded(
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        height: 3,
        decoration: BoxDecoration(
          color: activa ? AppColors.marca : AppColors.linea,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
    );
    return Row(
      children: [
        for (var i = 0; i < _pasos.length; i++)
          Expanded(
            child: Semantics(
              label:
                  'Paso ${i + 1}: ${_pasos[i].etiqueta}${i < actual
                      ? ', completado'
                      : i == actual
                      ? ', actual'
                      : ''}',
              excludeSemantics: true,
              child: Column(
                children: [
                  Row(
                    children: [
                      if (i == 0) const Spacer() else linea(i <= actual),
                      _Circulo(
                        paso: _pasos[i],
                        hecho: i < actual,
                        actual: i == actual,
                      ),
                      if (i == _pasos.length - 1)
                        const Spacer()
                      else
                        linea(i < actual),
                    ],
                  ),
                  const SizedBox(height: 4),
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      _pasos[i].etiqueta,
                      style: tema.bodySmall?.copyWith(
                        color: i <= actual
                            ? AppColors.tinta
                            : AppColors.tintaSuave,
                        fontWeight: i == actual
                            ? FontWeight.w700
                            : FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _Circulo extends StatelessWidget {
  const _Circulo({
    required this.paso,
    required this.hecho,
    required this.actual,
  });
  final _Paso paso;
  final bool hecho;
  final bool actual;

  @override
  Widget build(BuildContext context) {
    final lleno = hecho || actual;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      width: 30,
      height: 30,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: hecho
            ? AppColors.marca
            : (actual ? paso.color : AppColors.lineaSuave),
        border: actual ? Border.all(color: paso.tenue, width: 3) : null,
      ),
      child: Icon(
        hecho ? Icons.check : paso.icono,
        size: 15,
        color: lleno ? Colors.white : AppColors.tintaSuave,
      ),
    );
  }
}

class _Requisito extends StatelessWidget {
  const _Requisito({required this.texto, required this.cumple});
  final String texto;
  final bool cumple;

  @override
  Widget build(BuildContext context) {
    final color = cumple ? AppColors.exito : AppColors.tintaSuave;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          cumple ? Icons.check_circle : Icons.radio_button_unchecked,
          size: 15,
          color: color,
        ),
        const SizedBox(width: 4),
        Text(
          texto,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(color: color),
        ),
      ],
    );
  }
}

class _Exito extends StatelessWidget {
  const _Exito({required this.alIniciarSesion});
  final VoidCallback alIniciarSesion;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const SizedBox(height: 8),
        Center(
          child: Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.exitoFondo,
            ),
            child: const Icon(
              Icons.check_rounded,
              size: 40,
              color: AppColors.exito,
            ),
          ),
        ),
        const SizedBox(height: 20),
        Text(
          '¡Tu cuenta está lista!',
          textAlign: TextAlign.center,
          style: tema.headlineSmall,
        ),
        const SizedBox(height: 8),
        Text(
          r'Creamos tu usuario y tu cuenta bancaria GestoPago con saldo de $0.00. '
          'Inicia sesión con tu correo y tu contraseña para empezar.',
          textAlign: TextAlign.center,
          style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: alIniciarSesion,
          child: const Text('Ir a iniciar sesión'),
        ),
      ],
    );
  }
}
