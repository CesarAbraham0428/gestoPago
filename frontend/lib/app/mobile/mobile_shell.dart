import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../authentication/application/sesion_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/ui/breakpoints.dart';
import '../dependencias.dart';
import '../shell_comun.dart';

/// Marco de la aplicación móvil: barra superior y navegación inferior. En
/// tabletas horizontales la navegación pasa a un riel lateral compacto.
Widget construirShellMovil(
  BuildContext context,
  GoRouterState state,
  Widget contenido,
) {
  final sesion = DependenciasScope.of(context).sesion;
  final ancho = Breakpoints.esAncho(context, Breakpoints.barraLateral);

  return Scaffold(
    appBar: barraSuperior(
      context,
      state,
      sesion,
      menu: MenuCuentaMovil(sesion: sesion),
    ),
    body: ancho
        ? Row(
            children: [
              NavigationRail(
                backgroundColor: AppColors.superficie,
                selectedIndex: esNotificaciones(state) ? null : indiceDestino(state),
                onDestinationSelected: (i) => context.go(destinos[i].ruta),
                labelType: NavigationRailLabelType.all,
                indicatorColor: AppColors.marcaTenue,
                destinations: [
                  for (final d in destinos)
                    NavigationRailDestination(
                      icon: Icon(d.icono),
                      selectedIcon: Icon(d.iconoActivo),
                      label: Text(d.etiqueta),
                    ),
                ],
              ),
              const VerticalDivider(width: 1),
              Expanded(child: contenido),
            ],
          )
        : contenido,
    bottomNavigationBar: ancho || esDetalle(state)
        ? null
        : _BarraInferior(
            seleccionado: indiceDestino(state),
            alSeleccionar: (i) => context.go(destinos[i].ruta),
          ),
  );
}

/// Barra inferior con un fondo verde muy leve, filete superior y etiquetas de
/// una sola línea para que todas queden centradas bajo su icono.
class _BarraInferior extends StatelessWidget {
  const _BarraInferior({required this.seleccionado, required this.alSeleccionar});

  final int seleccionado;
  final ValueChanged<int> alSeleccionar;

  static const _fondo = Color(0xFFEAF2EE);

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final etiqueta = tema.textTheme.bodySmall!.copyWith(fontWeight: FontWeight.w600);
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: _fondo,
        border: Border(top: BorderSide(color: AppColors.linea)),
      ),
      // Con la fuente del sistema muy grande las etiquetas se cortarían.
      child: MediaQuery.withClampedTextScaling(
        maxScaleFactor: 1.15,
        child: NavigationBarTheme(
          data: tema.navigationBarTheme.copyWith(
            backgroundColor: _fondo,
            indicatorColor: const Color(0xFFCFE4D9),
            height: 68,
            labelTextStyle: WidgetStateProperty.resolveWith(
              (estados) => etiqueta.copyWith(
                color: estados.contains(WidgetState.selected)
                    ? AppColors.marca
                    : AppColors.tintaSuave,
              ),
            ),
            iconTheme: WidgetStateProperty.resolveWith(
              (estados) => IconThemeData(
                color: estados.contains(WidgetState.selected)
                    ? AppColors.marca
                    : AppColors.tintaSuave,
              ),
            ),
          ),
          child: NavigationBar(
            selectedIndex: seleccionado,
            onDestinationSelected: alSeleccionar,
            destinations: [
              for (final d in destinos)
                NavigationDestination(
                  icon: Icon(d.icono),
                  selectedIcon: Icon(d.iconoActivo),
                  label: d.etiquetaCorta ?? d.etiqueta,
                  tooltip: d.etiqueta,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

String _iniciales(String nombre, String correo) {
  final partes = nombre.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (partes.length >= 2) return (partes[0][0] + partes[1][0]).toUpperCase();
  if (partes.length == 1) return partes[0][0].toUpperCase();
  return correo.isEmpty ? '?' : correo[0].toUpperCase();
}

class _Avatar extends StatelessWidget {
  const _Avatar({required this.iniciales, required this.tamano});
  final String iniciales;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: Container(
        width: tamano,
        height: tamano,
        alignment: Alignment.center,
        decoration: const BoxDecoration(
          shape: BoxShape.circle,
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [AppColors.bosqueClaro, AppColors.marca],
          ),
        ),
        child: Text(
          iniciales,
          style: TextStyle(
            color: Colors.white,
            fontSize: tamano * 0.38,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.3,
          ),
        ),
      ),
    );
  }
}

/// Botón de cuenta de la barra superior: avatar con las iniciales del usuario
/// que abre una hoja inferior con sus datos y el cierre de sesión.
class MenuCuentaMovil extends StatelessWidget {
  const MenuCuentaMovil({super.key, required this.sesion});
  final SesionController sesion;

  @override
  Widget build(BuildContext context) {
    final datos = sesion.sesion;
    final iniciales = _iniciales(datos?.nombreCompleto ?? '', datos?.correo ?? '');
    return Tooltip(
      message: 'Tu cuenta',
      child: InkResponse(
        radius: 28,
        onTap: () => _abrir(context),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: _Avatar(iniciales: iniciales, tamano: 36),
        ),
      ),
    );
  }

  Future<void> _abrir(BuildContext context) async {
    final salir = await showModalBottomSheet<bool>(
      context: context,
      showDragHandle: true,
      backgroundColor: AppColors.superficie,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => _HojaCuenta(
        nombre: sesion.sesion?.nombreCompleto ?? '',
        correo: sesion.sesion?.correo ?? '',
      ),
    );
    if (salir == true && context.mounted) {
      await confirmarCierreDeSesion(context, sesion);
    }
  }
}

class _HojaCuenta extends StatelessWidget {
  const _HojaCuenta({required this.nombre, required this.correo});
  final String nombre;
  final String correo;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _Avatar(iniciales: _iniciales(nombre, correo), tamano: 72),
            const SizedBox(height: 14),
            Text(
              nombre.isNotEmpty ? nombre : 'Tu cuenta',
              style: tema.titleLarge,
              textAlign: TextAlign.center,
            ),
            if (correo.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                correo,
                style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
                textAlign: TextAlign.center,
              ),
            ],
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => Navigator.of(context).pop(true),
                icon: const Icon(Icons.logout),
                label: const Text('Cerrar sesión'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.rechazo,
                  backgroundColor: AppColors.rechazoFondo,
                  side: BorderSide.none,
                  minimumSize: const Size.fromHeight(52),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
