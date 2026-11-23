import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/campo_codigo.dart';
import '../../../core/ui/ilustracion.dart';
import '../../../core/ui/marco_acceso.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/sesion_controller.dart';
import '../../domain/entities/desafio_verificacion.dart';
import '../../domain/validadores_credenciales.dart';

/// Pantalla de acceso (adaptador de entrada). Las dos apps comparten esta
/// pantalla: el formulario va centrado y, en pantallas amplias, dentro de una
/// tarjeta. El acceso tiene dos pasos: credenciales y código enviado al correo.
class LoginPage extends StatelessWidget {
  const LoginPage({super.key, required this.sesion, this.alCrearCuenta});

  final SesionController sesion;

  /// Abre la pantalla de crear cuenta; sin ella no se muestra esa sección.
  final VoidCallback? alCrearCuenta;

  @override
  Widget build(BuildContext context) =>
      _ContenedorFormulario(sesion: sesion, alCrearCuenta: alCrearCuenta);
}

class _ContenedorFormulario extends StatefulWidget {
  const _ContenedorFormulario({required this.sesion, this.alCrearCuenta});
  final SesionController sesion;
  final VoidCallback? alCrearCuenta;

  @override
  State<_ContenedorFormulario> createState() => _ContenedorFormularioState();
}

class _ContenedorFormularioState extends State<_ContenedorFormulario> {
  /// Hay desafío cuando la contraseña ya fue validada: toca el código de la app.
  DesafioVerificacion? _desafio;

  @override
  Widget build(BuildContext context) {
    final desafio = _desafio;
    return MarcoAcceso(
      lema: 'Paga tus servicios y recargas en un solo lugar',
      tarjeta: desafio == null
          ? _PasoCredenciales(
              sesion: widget.sesion,
              alEnviarCodigo: (d) => setState(() => _desafio = d),
              alCrearCuenta: widget.alCrearCuenta,
            )
          : _PasoCodigo(
              sesion: widget.sesion,
              desafio: desafio,
              alVolver: () => setState(() => _desafio = null),
            ),
    );
  }
}

/// Mensaje de un error de la API, o uno genérico si fue otra cosa.
String _mensajeDe(Object error, String generico) =>
    error is AppException ? error.message : generico;

/// Espera obligatoria tras un 429 del servidor (demasiados intentos): bloquea
/// el envío y muestra la cuenta regresiva. Es solo una ayuda de usabilidad; el
/// límite real lo aplica el servidor, que rechaza igual desde cualquier cliente.
mixin _EsperaPorLimite<T extends StatefulWidget> on State<T> {
  static const _esperaPorOmision = Duration(seconds: 60);

  Timer? _reloj;
  int _segundos = 0;

  int get segundosDeEspera => _segundos;

  bool get enEspera => _segundos > 0;

  /// Si [error] es un 429 inicia la espera y devuelve true.
  bool iniciarEsperaSiLimite(Object error) {
    if (error is! ApiException || !error.esLimiteDeSolicitudes) return false;
    final espera = error.reintentarEn ?? _esperaPorOmision;
    _segundos = espera.inSeconds < 1 ? 1 : espera.inSeconds;
    _reloj?.cancel();
    // dispose cancela el temporizador, así que aquí el widget sigue montado.
    _reloj = Timer.periodic(const Duration(seconds: 1), (temporizador) {
      setState(() => _segundos--);
      if (_segundos <= 0) temporizador.cancel();
    });
    return true;
  }

  @override
  void dispose() {
    _reloj?.cancel();
    super.dispose();
  }
}

/// "45 s" o "2:05 min" para la cuenta regresiva del botón.
String _formatoEspera(int segundos) => segundos < 60
    ? '$segundos s'
    : '${segundos ~/ 60}:${(segundos % 60).toString().padLeft(2, '0')} min';

class _PasoCredenciales extends StatefulWidget {
  const _PasoCredenciales({
    required this.sesion,
    required this.alEnviarCodigo,
    this.alCrearCuenta,
  });
  final SesionController sesion;
  final ValueChanged<DesafioVerificacion> alEnviarCodigo;
  final VoidCallback? alCrearCuenta;

  @override
  State<_PasoCredenciales> createState() => _PasoCredencialesState();
}

class _PasoCredencialesState extends State<_PasoCredenciales>
    with _EsperaPorLimite {
  final _clave = GlobalKey<FormState>();
  final _correo = TextEditingController();
  final _password = TextEditingController();
  bool _ocultar = true;
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _correo.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _continuar() async {
    if (_enviando || enEspera || !(_clave.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final desafio = await widget.sesion.solicitarCodigo(
        correo: _correo.text,
        password: _password.text,
      );
      // La contraseña no se conserva: el segundo paso solo necesita el desafío.
      _password.clear();
      if (mounted) widget.alEnviarCodigo(desafio);
    } on Object catch (e) {
      if (mounted) {
        iniciarEsperaSiLimite(e);
        setState(
          () => _error = _mensajeDe(
            e,
            'No fue posible iniciar sesión. Inténtalo de nuevo.',
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final aviso = widget.sesion.aviso;
    return AutofillGroup(
      child: Form(
        key: _clave,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Center(
                  child: Ilustracion(
                    icono: Icons.lock_open_outlined,
                    color: AppColors.marca,
                    fondo: AppColors.marcaTenue,
                    tamano: 96,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Inicia sesión',
                  style: tema.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 18),
                if (_error != null || aviso != null) ...[
                  AvisoEstado(
                    tono: _error != null ? Tono.rechazo : Tono.aviso,
                    icono: _error != null
                        ? Icons.error_outline
                        : Icons.info_outline,
                    texto: (_error ?? aviso)!,
                  ),
                  const SizedBox(height: 12),
                ],
                TextFormField(
                  controller: _correo,
                  // Las `key` evitan que el aviso de error, al insertarse encima, haga que
                  // Flutter recree los campos con el foco puesto: en web el input del
                  // navegador quedaba desincronizado y dejaba de recibir texto.
                  key: const ValueKey('login-correo'),
                  readOnly: _enviando, // no `enabled`: desactivar quita el foco y en web el campo deja de recibir texto
                  keyboardType: TextInputType.emailAddress,
                  textInputAction: TextInputAction.next,
                  autofillHints: const [AutofillHints.email],
                  autocorrect: false,
                  inputFormatters: [LengthLimitingTextInputFormatter(100)],
                  decoration: const InputDecoration(
                    labelText: 'Correo electrónico',
                    prefixIcon: Icon(Icons.mail_outline, color: AppColors.marca),
                  ),
                  validator: ValidadoresCredenciales.correo,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  key: const ValueKey('login-password'),
                  controller: _password,
                  readOnly: _enviando,
                  obscureText: _ocultar,
                  textInputAction: TextInputAction.done,
                  autofillHints: const [AutofillHints.password],
                  enableSuggestions: false,
                  autocorrect: false,
                  inputFormatters: [LengthLimitingTextInputFormatter(72)],
                  onFieldSubmitted: (_) => _continuar(),
                  decoration: InputDecoration(
                    labelText: 'Contraseña',
                    prefixIcon: const Icon(
                      Icons.lock_outline,
                      color: AppColors.marca,
                    ),
                    suffixIcon: IconButton(
                      tooltip: _ocultar
                          ? 'Mostrar contraseña'
                          : 'Ocultar contraseña',
                      icon: Icon(
                        _ocultar
                            ? Icons.visibility_outlined
                            : Icons.visibility_off_outlined,
                      ),
                      onPressed: () => setState(() => _ocultar = !_ocultar),
                    ),
                  ),
                  validator: ValidadoresCredenciales.password,
                ),
                const SizedBox(height: 18),
                _BotonEnvio(
                  enviando: _enviando,
                  etiqueta: 'Continuar',
                  alPulsar: _continuar,
                  esperaSegundos: segundosDeEspera,
                ),
              ],
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (widget.alCrearCuenta != null) ...[
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      const Expanded(child: Divider()),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text(
                          '¿Aún no tienes cuenta?',
                          style: tema.bodySmall,
                        ),
                      ),
                      const Expanded(child: Divider()),
                    ],
                  ),
                  const SizedBox(height: 10),
                  FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.turquesa,
                      foregroundColor: Colors.white,
                    ),
                    onPressed: _enviando ? null : widget.alCrearCuenta,
                    icon: const Icon(Icons.person_add_alt_1_outlined, size: 20),
                    label: const Text('Crear cuenta'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _PasoCodigo extends StatefulWidget {
  const _PasoCodigo({
    required this.sesion,
    required this.desafio,
    required this.alVolver,
  });

  final SesionController sesion;
  final DesafioVerificacion desafio;
  final VoidCallback alVolver;

  @override
  State<_PasoCodigo> createState() => _PasoCodigoState();
}

class _PasoCodigoState extends State<_PasoCodigo> with _EsperaPorLimite {
  final _clave = GlobalKey<FormState>();
  final _codigo = TextEditingController();
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _verificar({bool cerrarSesionAnterior = false}) async {
    if (_enviando || enEspera || !(_clave.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      await widget.sesion.verificarCodigo(
        desafio: widget.desafio,
        codigo: _codigo.text,
        cerrarSesionAnterior: cerrarSesionAnterior,
      );
      // El enrutador reacciona al cambio de sesión; no se navega aquí.
    } on SesionActivaException {
      if (!mounted) return;
      setState(() => _enviando = false);
      await _confirmarCierreDeOtraSesion();
    } on Object catch (e) {
      if (mounted) {
        iniciarEsperaSiLimite(e);
        setState(
          () => _error = _mensajeDe(
            e,
            'No fue posible verificar el código. Inténtalo de nuevo.',
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  /// Un usuario no puede tener dos sesiones: se pide cerrar la anterior antes de entrar.
  Future<void> _confirmarCierreDeOtraSesion() async {
    final cerrar = await showDialog<bool>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.72), // el fondo se atenúa para que solo se vea la alerta
      builder: (dialogo) {
        final tema = Theme.of(dialogo).textTheme;
        return Dialog(
          clipBehavior: Clip.antiAlias,
          insetPadding: const EdgeInsets.all(20),
          backgroundColor: AppColors.superficie,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(24),
            side: const BorderSide(color: AppColors.coral, width: 2),
          ),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    padding: const EdgeInsets.fromLTRB(24, 28, 24, 22),
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [AppColors.coral, AppColors.rechazo],
                      ),
                    ),
                    child: Column(
                      children: [
                        // Entrada con rebote una sola vez (sin bucle: no bloquea las pruebas).
                        TweenAnimationBuilder<double>(
                          tween: Tween(begin: 0.4, end: 1),
                          duration: const Duration(milliseconds: 700),
                          curve: Curves.elasticOut,
                          builder: (_, escala, hijo) =>
                              Transform.scale(scale: escala, child: hijo),
                          child: Container(
                            width: 76,
                            height: 76,
                            decoration: const BoxDecoration(
                              color: Colors.white,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.warning_amber_rounded,
                              size: 46,
                              color: AppColors.rechazo,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Ya tienes una sesión abierta',
                          textAlign: TextAlign.center,
                          style: tema.titleLarge?.copyWith(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: AppColors.coralTenue,
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Icon(
                                Icons.devices_other,
                                color: AppColors.rechazo,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  'Tu cuenta está abierta en otro dispositivo o navegador. '
                                  'Solo puede haber una sesión a la vez: si continúas, la otra se cerrará.',
                                  style: tema.bodyMedium?.copyWith(
                                    color: AppColors.tinta,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 18),
                        FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.rechazo,
                            foregroundColor: Colors.white,
                          ),
                          onPressed: () => Navigator.of(dialogo).pop(true),
                          child: const Text('Cerrar la otra sesión y continuar'),
                        ),
                        TextButton(
                          onPressed: () => Navigator.of(dialogo).pop(false),
                          child: const Text('Cancelar'),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          ),
        );
      },
    );
    if (cerrar == true && mounted) await _verificar(cerrarSesionAnterior: true);
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final enrolamiento = widget.desafio.enrolamiento;
    return Form(
      key: _clave,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Center(
                child: Ilustracion(
                  icono: Icons.phonelink_lock_outlined,
                  color: AppColors.marca,
                  fondo: AppColors.marcaTenue,
                  tamano: 96,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                enrolamiento == null
                    ? 'Código de tu app'
                    : 'Enlaza tu app de autenticación',
                style: tema.headlineSmall,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              if (enrolamiento == null)
                Text(
                  'Abre tu app de autenticación (Google Authenticator, '
                  'Microsoft Authenticator o Authy) y escribe el código de '
                  '6 dígitos de GestoPago.',
                  style: tema.bodyMedium?.copyWith(
                    color: AppColors.tintaSuave,
                  ),
                  textAlign: TextAlign.center,
                )
              else
                _EnlaceDeApp(enrolamiento: enrolamiento),
              const SizedBox(height: 10),
              Text(
                'Tienes ${widget.desafio.vigencia.inMinutes} minutos para '
                'terminar este paso.',
                style: tema.bodySmall?.copyWith(color: AppColors.tintaSuave),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              if (_error != null) ...[
                AvisoEstado(
                  tono: Tono.rechazo,
                  icono: Icons.error_outline,
                  texto: _error!,
                ),
                const SizedBox(height: 20),
              ],
              CampoCodigo(
                key: const ValueKey('login-codigo'), // el aviso de error se inserta encima: ver los campos del paso 1
                controller: _codigo,
                enabled: !_enviando,
                autofocus: true,
                validator: ValidadoresCredenciales.codigo,
                alEnviar: _verificar,
              ),
              const SizedBox(height: 24),
              _BotonEnvio(
                enviando: _enviando,
                etiqueta: 'Verificar y entrar',
                alPulsar: _verificar,
                esperaSegundos: segundosDeEspera,
              ),
            ],
          ),
          TextButton(
            onPressed: _enviando ? null : widget.alVolver,
            child: const Text('Volver e iniciar de nuevo'),
          ),
        ],
      ),
    );
  }
}

/// Instrucciones, código QR y clave manual para enlazar la app de autenticación.
/// En el celular el QR no se puede escanear con la misma pantalla: ahí sirve la clave.
class _EnlaceDeApp extends StatelessWidget {
  const _EnlaceDeApp({required this.enrolamiento});

  final EnrolamientoTotp enrolamiento;

  /// "ABCD EFGH IJKL…": la clave en bloques de 4 es más fácil de copiar a mano.
  String get _claveEnBloques => RegExp(
    r'.{1,4}',
  ).allMatches(enrolamiento.secreto).map((m) => m[0]).join(' ');

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '1. Instala Google Authenticator, Microsoft Authenticator o Authy.\n'
          '2. Escanea este código QR, o escribe la clave de abajo si estás en '
          'el mismo celular.\n'
          '3. Escribe el código de 6 dígitos que aparece en la app.',
          style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
        ),
        const SizedBox(height: 14),
        Center(
          child: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.linea),
            ),
            child: Semantics(
              label: 'Código QR para enlazar tu app de autenticación',
              child: QrImageView(
                data: enrolamiento.otpauthUri,
                size: 168,
                backgroundColor: Colors.white,
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.fromLTRB(14, 6, 6, 6),
          decoration: BoxDecoration(
            color: AppColors.marcaTenue,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Expanded(
                child: SelectableText(
                  _claveEnBloques,
                  key: const ValueKey('clave-totp'),
                  style: tema.titleSmall?.copyWith(
                    color: AppColors.marcaOscura,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.2,
                  ),
                ),
              ),
              IconButton(
                tooltip: 'Copiar clave',
                icon: const Icon(Icons.copy_rounded, size: 20),
                color: AppColors.marca,
                onPressed: () async {
                  final aviso = ScaffoldMessenger.of(context);
                  await Clipboard.setData(
                    ClipboardData(text: enrolamiento.secreto),
                  );
                  aviso.showSnackBar(
                    const SnackBar(content: Text('Clave copiada')),
                  );
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _BotonEnvio extends StatelessWidget {
  const _BotonEnvio({
    required this.enviando,
    required this.etiqueta,
    required this.alPulsar,
    this.esperaSegundos = 0,
  });

  final bool enviando;
  final String etiqueta;
  final VoidCallback alPulsar;

  /// Mientras sea mayor que cero el servidor pidió esperar: el botón se desactiva.
  final int esperaSegundos;

  @override
  Widget build(BuildContext context) {
    if (esperaSegundos > 0) {
      return FilledButton(
        onPressed: null,
        child: Text('Reintenta en ${_formatoEspera(esperaSegundos)}'),
      );
    }
    return FilledButton(
      onPressed: enviando ? null : alPulsar,
      child: enviando
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: AppColors.tintaSuave,
              ),
            )
          : Text(etiqueta),
    );
  }
}
