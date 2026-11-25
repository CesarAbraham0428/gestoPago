import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:toastification/toastification.dart';

import '../authentication/infraestructure/ui/guardia_inactividad.dart';
import '../core/config/app_config.dart';
import '../core/theme/app_theme.dart';
import 'dependencias.dart';
import 'router.dart';
import 'shell_comun.dart';

/// Arranque común de las dos aplicaciones; cada punto de entrada solo elige
/// el marco de navegación ([shell]).
///
/// [produccion] y [conexionPermitida] existen para poder probar la negativa a
/// arrancar sin HTTPS; en uso normal se toman del modo de compilación y de
/// [AppConfig].
Future<void> iniciarAplicacion(
  ConstructorShell shell, {
  bool produccion = kReleaseMode,
  bool? conexionPermitida,
}) async {
  WidgetsFlutterBinding.ensureInitialized();
  if (produccion && !(conexionPermitida ?? AppConfig.conexionPermitida)) {
    runApp(const _ConfiguracionInsegura());
    return;
  }
  await initializeDateFormatting('es_MX');
  final dependencias = Dependencias.crear();
  await dependencias.sesion.restaurar();
  runApp(GestoPagoApp(dependencias: dependencias, shell: shell));
}

class GestoPagoApp extends StatefulWidget {
  const GestoPagoApp({super.key, required this.dependencias, required this.shell});

  final Dependencias dependencias;
  final ConstructorShell shell;

  @override
  State<GestoPagoApp> createState() => _GestoPagoAppState();
}

class _GestoPagoAppState extends State<GestoPagoApp> {
  late final GoRouter _router = crearRouter(widget.dependencias, widget.shell);

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DependenciasScope(
      dependencias: widget.dependencias,
      child: ToastificationWrapper(
        child: MaterialApp.router(
          title: 'GestoPago',
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(),
          routerConfig: _router,
          builder: (context, child) => GuardiaInactividad(
            sesion: widget.dependencias.sesion,
            limite: AppConfig.inactividadMaxima,
            child: child ?? const SizedBox.shrink(),
          ),
          locale: const Locale('es', 'MX'),
          supportedLocales: const [Locale('es', 'MX'), Locale('es')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
        ),
      ),
    );
  }
}

/// Se muestra en lugar de la app si una compilación de producción apunta a una
/// API sin HTTPS: es preferible no arrancar a enviar credenciales en claro.
class _ConfiguracionInsegura extends StatelessWidget {
  const _ConfiguracionInsegura();

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: Center(
          child: Padding(
            padding: EdgeInsets.all(32),
            child: Text(
              'Esta versión no puede conectarse de forma segura. '
              'Instala una versión que use HTTPS.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}
