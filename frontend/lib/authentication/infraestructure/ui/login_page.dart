import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/breakpoints.dart';
import '../../../core/ui/campo_codigo.dart';
import '../../../core/ui/logo_app.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/sesion_controller.dart';
import '../../domain/entities/desafio_verificacion.dart';
import '../../domain/validadores_credenciales.dart';

/// Pantalla de acceso (adaptador de entrada). Las dos apps comparten esta
/// pantalla: el formulario va centrado y, en pantallas amplias, dentro de una
/// tarjeta. El acceso tiene dos pasos: credenciales y código enviado al correo.
class LoginPage extends StatelessWidget {
  const LoginPage({super.key, required this.sesion});

  final SesionController sesion;

  @override
  Widget build(BuildContext context) =>
      Scaffold(body: _ContenedorFormulario(sesion: sesion));
}

class _ContenedorFormulario extends StatefulWidget {
  const _ContenedorFormulario({required this.sesion});
  final SesionController sesion;

  @override
  State<_ContenedorFormulario> createState() => _ContenedorFormularioState();
}

class _ContenedorFormularioState extends State<_ContenedorFormulario> {
  /// Hay desafío cuando el servidor ya envió el código: toca el segundo paso.
  DesafioVerificacion? _desafio;

  @override
  Widget build(BuildContext context) {
    final desafio = _desafio;
    final ancho = Breakpoints.esAncho(context, Breakpoints.barraLateral);
    final Widget paso = desafio == null
        ? _PasoCredenciales(
            sesion: widget.sesion,
            alEnviarCodigo: (d) => setState(() => _desafio = d),
          )
        : _PasoCodigo(
            sesion: widget.sesion,
            desafio: desafio,
            alVolver: () => setState(() => _desafio = null),
          );
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.bosqueClaro, AppColors.bosque],
        ),
      ),
      child: CustomPaint(
        painter: const _FondoDecorado(),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 40),
              child: ConstrainedBox(
                constraints: BoxConstraints(maxWidth: ancho ? 440 : 400),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Center(child: LogoApp(tamano: 44, sobreMarca: true)),
                    const SizedBox(height: 10),
                    Text(
                      'Paga tus servicios y recargas en un solo lugar',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Colors.white.withValues(alpha: 0.78),
                      ),
                    ),
                    const SizedBox(height: 28),
                    Container(
                      padding: ancho
                          ? const EdgeInsets.fromLTRB(36, 36, 36, 28)
                          : const EdgeInsets.fromLTRB(22, 26, 22, 20),
                      decoration: BoxDecoration(
                        color: AppColors.superficie,
                        borderRadius: BorderRadius.circular(18),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.25),
                            blurRadius: 32,
                            offset: const Offset(0, 14),
                          ),
                        ],
                      ),
                      child: paso,
                    ),
                    const SizedBox(height: 20),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.lock_outline, size: 14, color: Colors.white.withValues(alpha: 0.7)),
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            'Conexión cifrada y verificación en dos pasos',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Colors.white.withValues(alpha: 0.7),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Círculos suaves con los colores de las secciones de la app, detrás del
/// formulario. Solo decoran: no compiten con la tarjeta blanca.
class _FondoDecorado extends CustomPainter {
  const _FondoDecorado();

  @override
  void paint(Canvas canvas, Size s) {
    void circulo(double x, double y, double r, Color c, double alfa) => canvas.drawCircle(
      Offset(s.width * x, s.height * y),
      r,
      Paint()..color = c.withValues(alpha: alfa),
    );
    circulo(0.08, 0.12, 180, AppColors.turquesa, 0.28);
    circulo(0.95, 0.85, 220, AppColors.azul, 0.20);
    circulo(0.88, 0.10, 70, AppColors.coral, 0.22);
    circulo(0.12, 0.92, 90, AppColors.ciruela, 0.22);
    final anillo = Paint()
      ..color = Colors.white.withValues(alpha: 0.07)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    canvas
      ..drawCircle(Offset(s.width * 0.82, s.height * 0.35), 140, anillo)
      ..drawCircle(Offset(s.width * 0.20, s.height * 0.62), 110, anillo);
  }

  @override
  bool shouldRepaint(_FondoDecorado oldDelegate) => false;
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
    _reloj = Timer.periodic(const Duration(seconds: 1), (temporizador) {
      if (!mounted) {
        temporizador.cancel();
        return;
      }
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
  const _PasoCredenciales({required this.sesion, required this.alEnviarCodigo});
  final SesionController sesion;
  final ValueChanged<DesafioVerificacion> alEnviarCodigo;

  @override
  State<_PasoCredenciales> createState() => _PasoCredencialesState();
}

class _PasoCredencialesState extends State<_PasoCredenciales> with _EsperaPorLimite {
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
    if (_enviando || enEspera || !(_clave.currentState?.validate() ?? false)) return;
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
        setState(() => _error = _mensajeDe(e, 'No fue posible iniciar sesión. Inténtalo de nuevo.'));
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
          children: [
            Text('Inicia sesión', style: tema.headlineMedium),
            const SizedBox(height: 8),
            Text(
              'Usa el correo y la contraseña de tu cuenta. Después te pediremos un código de verificación.',
              style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
            ),
            const SizedBox(height: 28),
            if (_error != null || aviso != null) ...[
              AvisoEstado(
                tono: _error != null ? Tono.rechazo : Tono.aviso,
                icono: _error != null ? Icons.error_outline : Icons.info_outline,
                texto: (_error ?? aviso)!,
              ),
              const SizedBox(height: 20),
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
              decoration: const InputDecoration(labelText: 'Correo electrónico'),
              validator: ValidadoresCredenciales.correo,
            ),
            const SizedBox(height: 16),
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
                suffixIcon: IconButton(
                  tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
                  icon: Icon(
                    _ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                  ),
                  onPressed: () => setState(() => _ocultar = !_ocultar),
                ),
              ),
              validator: ValidadoresCredenciales.password,
            ),
            const SizedBox(height: 24),
            _BotonEnvio(
              enviando: _enviando,
              etiqueta: 'Continuar',
              alPulsar: _continuar,
              esperaSegundos: segundosDeEspera,
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
    if (_enviando || enEspera || !(_clave.currentState?.validate() ?? false)) return;
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
        setState(() => _error = _mensajeDe(e, 'No fue posible verificar el código. Inténtalo de nuevo.'));
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  /// Un usuario no puede tener dos sesiones: se pide cerrar la anterior antes de entrar.
  Future<void> _confirmarCierreDeOtraSesion() async {
    final cerrar = await showDialog<bool>(
      context: context,
      builder: (dialogo) => AlertDialog(
        title: const Text('Ya tienes una sesión abierta'),
        content: const Text(
          'Tu cuenta está abierta en otro dispositivo o navegador. '
          'Solo puede haber una sesión a la vez: si continúas, la otra se cerrará.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogo).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogo).pop(true),
            child: const Text('Cerrar la otra sesión y continuar'),
          ),
        ],
      ),
    );
    if (cerrar == true && mounted) await _verificar(cerrarSesionAnterior: true);
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Form(
      key: _clave,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Revisa tu correo', style: tema.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'Enviamos un código de 6 dígitos a ${widget.desafio.correoEnmascarado}. '
            'Vence en ${widget.desafio.vigencia.inMinutes} minutos y solo sirve una vez.',
            style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
          ),
          const SizedBox(height: 28),
          if (_error != null) ...[
            AvisoEstado(tono: Tono.rechazo, icono: Icons.error_outline, texto: _error!),
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
          const SizedBox(height: 8),
          TextButton(
            onPressed: _enviando ? null : widget.alVolver,
            child: const Text('Volver e iniciar de nuevo'),
          ),
        ],
      ),
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
