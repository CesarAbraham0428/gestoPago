import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/breakpoints.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/sesion_controller.dart';
import '../../domain/entities/desafio_verificacion.dart';
import '../../domain/validadores_credenciales.dart';

/// Pantalla de acceso (adaptador de entrada). Las dos apps comparten esta
/// pantalla; en ancho amplio añade un panel lateral con lo que se puede hacer.
/// El acceso tiene dos pasos: credenciales y código enviado al correo.
class LoginPage extends StatelessWidget {
  const LoginPage({super.key, required this.sesion});

  final SesionController sesion;

  @override
  Widget build(BuildContext context) {
    final ancho = Breakpoints.esAncho(context, Breakpoints.barraLateral);
    return Scaffold(
      body: ancho
          ? Row(
              children: [
                const Expanded(flex: 5, child: _PanelMarca()),
                Expanded(flex: 6, child: _ContenedorFormulario(sesion: sesion)),
              ],
            )
          : _ContenedorFormulario(sesion: sesion),
    );
  }
}

class _PanelMarca extends StatelessWidget {
  const _PanelMarca();

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    const blanco = Colors.white;
    final suave = blanco.withValues(alpha: 0.78);
    return Container(
      color: AppColors.marca,
      padding: const EdgeInsets.all(56),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            'GestoPago',
            style: tema.titleLarge?.copyWith(color: blanco, fontSize: 22),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Recarga tu celular y paga tus servicios desde un solo lugar.',
                style: tema.headlineMedium?.copyWith(color: blanco, fontSize: 34),
              ),
              const SizedBox(height: 28),
              for (final linea in const [
                'Elige el servicio y confirma con tu PIN.',
                'Cada compra queda en tu historial con su referencia.',
                'Tu saldo se descuenta solo cuando el pago se confirma.',
              ])
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.check, size: 18, color: suave),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          linea,
                          style: tema.bodyLarge?.copyWith(color: suave),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox.shrink(),
        ],
      ),
    );
  }
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
    final tema = Theme.of(context).textTheme;
    final desafio = _desafio;
    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!Breakpoints.esAncho(context, Breakpoints.barraLateral)) ...[
                  Text(
                    'GestoPago',
                    style: tema.titleLarge?.copyWith(color: AppColors.marca),
                  ),
                  const SizedBox(height: 32),
                ],
                if (desafio == null)
                  _PasoCredenciales(
                    sesion: widget.sesion,
                    alEnviarCodigo: (d) => setState(() => _desafio = d),
                  )
                else
                  _PasoCodigo(
                    sesion: widget.sesion,
                    desafio: desafio,
                    alVolver: () => setState(() => _desafio = null),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Mensaje de un error de la API, o uno genérico si fue otra cosa.
String _mensajeDe(Object error, String generico) =>
    error is AppException ? error.message : generico;

class _PasoCredenciales extends StatefulWidget {
  const _PasoCredenciales({required this.sesion, required this.alEnviarCodigo});
  final SesionController sesion;
  final ValueChanged<DesafioVerificacion> alEnviarCodigo;

  @override
  State<_PasoCredenciales> createState() => _PasoCredencialesState();
}

class _PasoCredencialesState extends State<_PasoCredenciales> {
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
    if (_enviando || !(_clave.currentState?.validate() ?? false)) return;
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
              enabled: !_enviando,
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
              controller: _password,
              enabled: !_enviando,
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
            _BotonEnvio(enviando: _enviando, etiqueta: 'Continuar', alPulsar: _continuar),
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

class _PasoCodigoState extends State<_PasoCodigo> {
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
    if (_enviando || !(_clave.currentState?.validate() ?? false)) return;
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
          TextFormField(
            controller: _codigo,
            enabled: !_enviando,
            autofocus: true,
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.done,
            autofillHints: const [AutofillHints.oneTimeCode],
            enableSuggestions: false,
            autocorrect: false,
            textAlign: TextAlign.center,
            style: tema.headlineSmall?.copyWith(letterSpacing: 8),
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            onFieldSubmitted: (_) => _verificar(),
            decoration: const InputDecoration(labelText: 'Código de verificación'),
            validator: ValidadoresCredenciales.codigo,
          ),
          const SizedBox(height: 24),
          _BotonEnvio(
            enviando: _enviando,
            etiqueta: 'Verificar y entrar',
            alPulsar: _verificar,
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
  });

  final bool enviando;
  final String etiqueta;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) {
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
