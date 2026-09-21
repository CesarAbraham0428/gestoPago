import 'dart:convert';

import 'package:flutter/material.dart';

import 'auth_service.dart';
import 'products_page.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.authService});

  final AuthService authService;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();
  final _name = TextEditingController();
  final _paternalSurname = TextEditingController();
  final _maternalSurname = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();

  bool _registering = false;
  bool _passwordVisible = false;
  bool _busy = false;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    _name.dispose();
    _paternalSurname.dispose();
    _maternalSurname.dispose();
    _email.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _busy = true);
    try {
      final session = _registering
          ? await widget.authService.register(
              username: _username.text,
              password: _password.text,
              name: _name.text,
              paternalSurname: _paternalSurname.text,
              maternalSurname: _maternalSurname.text,
              email: _email.text,
              phone: _phone.text,
            )
          : await widget.authService.login(_username.text, _password.text);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) =>
              ProductsPage(session: session, authService: widget.authService),
        ),
      );
    } on ApiException catch (error) {
      if (mounted) _showMessage(error.message);
    } on Object {
      if (mounted) {
        _showMessage(
          'Ocurrió un error. Revisa la conexión e inténtalo de nuevo.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String? _required(String? value, String label) {
    if (value == null || value.trim().isEmpty) return 'Escribe $label.';
    return null;
  }

  Widget _field({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    String? Function(String?)? validator,
    TextInputType? keyboardType,
    bool obscure = false,
    Widget? suffix,
    TextCapitalization capitalization = TextCapitalization.none,
  }) {
    return TextFormField(
      controller: controller,
      obscureText: obscure,
      keyboardType: keyboardType,
      textCapitalization: capitalization,
      validator: validator,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        suffixIcon: suffix,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(28),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(36, 34, 36, 28),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Center(
                          child: Container(
                            width: 58,
                            height: 58,
                            decoration: BoxDecoration(
                              color: colors.primary,
                              borderRadius: BorderRadius.circular(18),
                            ),
                            child: const Icon(
                              Icons.account_balance_wallet_rounded,
                              color: Colors.white,
                              size: 30,
                            ),
                          ),
                        ),
                        const SizedBox(height: 18),
                        Text(
                          _registering
                              ? 'Crea tu cuenta'
                              : 'Bienvenido a GestoPago',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineSmall
                              ?.copyWith(
                                fontWeight: FontWeight.w700,
                                letterSpacing: -0.5,
                              ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _registering
                              ? 'Regístrate para consultar el catálogo de productos.'
                              : 'Inicia sesión para consultar el catálogo de productos.',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: colors.onSurfaceVariant),
                        ),
                        const SizedBox(height: 28),
                        if (_registering) ...[
                          _field(
                            controller: _name,
                            label: 'Nombre(s)',
                            icon: Icons.person_outline_rounded,
                            capitalization: TextCapitalization.words,
                            validator: (value) => _required(value, 'tu nombre'),
                          ),
                          const SizedBox(height: 14),
                          _field(
                            controller: _paternalSurname,
                            label: 'Apellido paterno',
                            icon: Icons.badge_outlined,
                            capitalization: TextCapitalization.words,
                            validator: (value) =>
                                _required(value, 'tu apellido paterno'),
                          ),
                          const SizedBox(height: 14),
                          _field(
                            controller: _maternalSurname,
                            label: 'Apellido materno',
                            icon: Icons.badge_outlined,
                            capitalization: TextCapitalization.words,
                            validator: (value) =>
                                _required(value, 'tu apellido materno'),
                          ),
                          const SizedBox(height: 14),
                          _field(
                            controller: _email,
                            label: 'Correo electrónico',
                            icon: Icons.mail_outline_rounded,
                            keyboardType: TextInputType.emailAddress,
                            validator: (value) {
                              final missing = _required(value, 'tu correo');
                              if (missing != null) return missing;
                              if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')
                                  .hasMatch(value!.trim())) {
                                return 'Escribe un correo válido.';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 14),
                          _field(
                            controller: _phone,
                            label: 'Teléfono',
                            icon: Icons.phone_outlined,
                            keyboardType: TextInputType.phone,
                            validator: (value) {
                              final missing = _required(value, 'tu teléfono');
                              if (missing != null) return missing;
                              if (!RegExp(r'^\+?[0-9]{10,15}$')
                                  .hasMatch(value!.trim())) {
                                return 'Usa entre 10 y 15 dígitos.';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 14),
                        ],
                        _field(
                          controller: _username,
                          label: 'Usuario',
                          icon: Icons.alternate_email_rounded,
                          validator: (value) {
                            final missing = _required(value, 'tu usuario');
                            if (missing != null) return missing;
                            if (value!.trim().length < 3) {
                              return 'Debe tener al menos 3 caracteres.';
                            }
                            if (!RegExp(r'^[a-zA-Z0-9._-]+$')
                                .hasMatch(value.trim())) {
                              return 'Usa letras, números, punto, guion o guion bajo.';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 14),
                        _field(
                          controller: _password,
                          label: 'Contraseña',
                          icon: Icons.lock_outline_rounded,
                          obscure: !_passwordVisible,
                          validator: (value) {
                            final missing = _required(value, 'tu contraseña');
                            if (missing != null) return missing;
                            if (value!.length < 8) {
                              return 'Debe tener al menos 8 caracteres.';
                            }
                            if (utf8.encode(value).length > 72) {
                              return 'La contraseña no puede superar 72 bytes.';
                            }
                            return null;
                          },
                          suffix: IconButton(
                            tooltip: _passwordVisible
                                ? 'Ocultar contraseña'
                                : 'Mostrar contraseña',
                            onPressed: () => setState(
                              () => _passwordVisible = !_passwordVisible,
                            ),
                            icon: Icon(
                              _passwordVisible
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
                        if (_registering) ...[
                          const SizedBox(height: 14),
                          _field(
                            controller: _confirmPassword,
                            label: 'Confirma tu contraseña',
                            icon: Icons.lock_reset_rounded,
                            obscure: !_passwordVisible,
                            validator: (value) => value != _password.text
                                ? 'Las contraseñas no coinciden.'
                                : null,
                          ),
                        ],
                        const SizedBox(height: 22),
                        FilledButton(
                          onPressed: _busy ? null : _submit,
                          style: FilledButton.styleFrom(
                            minimumSize: const Size.fromHeight(52),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                            ),
                          ),
                          child: _busy
                              ? const SizedBox(
                                  width: 22,
                                  height: 22,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : Text(
                                  _registering
                                      ? 'Crear cuenta'
                                      : 'Iniciar sesión',
                                ),
                        ),
                        const SizedBox(height: 14),
                        TextButton(
                          onPressed: _busy
                              ? null
                              : () => setState(
                                  () => _registering = !_registering,
                                ),
                          child: Text(
                            _registering
                                ? '¿Ya tienes cuenta? Inicia sesión'
                                : '¿Primera vez aquí? Crea una cuenta',
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Puedes iniciar sesión sin conexión después de entrar en este equipo. La sesión local vence en 7 días.',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: colors.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
