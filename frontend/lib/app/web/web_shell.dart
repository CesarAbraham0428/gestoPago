import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../authentication/application/sesion_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/ui/breakpoints.dart';
import '../../core/ui/ilustracion.dart';
import '../../core/ui/monograma.dart';
import '../../core/ui/logo_app.dart';
import '../dependencias.dart';
import '../shell_comun.dart';

/// Marco de la aplicación web: barra lateral fija en pantallas anchas y
/// navegación inferior con barra superior cuando la ventana es angosta.
Widget construirShellWeb(
  BuildContext context,
  GoRouterState state,
  Widget contenido,
) {
  final sesion = DependenciasScope.of(context).sesion;
  final conBarraLateral = Breakpoints.esAncho(
    context,
    Breakpoints.barraLateral,
  );

  if (conBarraLateral) {
    return _MarcoAncho(state: state, sesion: sesion, contenido: contenido);
  }

  return Scaffold(
    appBar: barraSuperior(context, state, sesion),
    body: contenido,
    bottomNavigationBar: esDetalle(state)
        ? null
        : NavigationBar(
            selectedIndex: indiceDestino(state),
            onDestinationSelected: (i) => context.go(destinos[i].ruta),
            destinations: [
              for (final d in destinos)
                NavigationDestination(
                  icon: Icon(d.icono),
                  selectedIcon: Icon(d.iconoActivo),
                  label: d.etiqueta,
                ),
            ],
          ),
  );
}

/// Pantalla ancha: la barra lateral se muestra u oculta con el botón de menú.
/// Es un StatefulWidget en la misma posición del árbol, así que el estado
/// abierto/cerrado se conserva al navegar entre secciones.
class _MarcoAncho extends StatefulWidget {
  const _MarcoAncho({
    required this.state,
    required this.sesion,
    required this.contenido,
  });

  final GoRouterState state;
  final SesionController sesion;
  final Widget contenido;

  @override
  State<_MarcoAncho> createState() => _MarcoAnchoState();
}

class _MarcoAnchoState extends State<_MarcoAncho> {
  static const _anchoMenu = 248.0;
  bool _menuAbierto = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOutCubic,
            width: _menuAbierto ? _anchoMenu + 1 : 0,
            child: ClipRect(
              child: OverflowBox(
                alignment: Alignment.centerRight,
                minWidth: _anchoMenu + 1,
                maxWidth: _anchoMenu + 1,
                child: Visibility(
                  visible: _menuAbierto,
                  maintainState: true,
                  maintainAnimation: true,
                  maintainSize: true,
                  child: Row(
                    children: [
                      _BarraLateral(state: widget.state, sesion: widget.sesion),
                      const VerticalDivider(width: 1),
                    ],
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Column(
              children: [
                _CabeceraContenido(
                  destino: destinos[indiceDestino(widget.state)],
                  menuAbierto: _menuAbierto,
                  alAlternarMenu: () => setState(() => _menuAbierto = !_menuAbierto),
                ),
                Expanded(child: widget.contenido),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CabeceraContenido extends StatelessWidget {
  const _CabeceraContenido({
    required this.destino,
    required this.menuAbierto,
    required this.alAlternarMenu,
  });
  final DestinoNavegacion destino;
  final bool menuAbierto;
  final VoidCallback alAlternarMenu;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Container(
      height: 72,
      padding: const EdgeInsets.only(left: 12, right: 24),
      decoration: BoxDecoration(
        border: const Border(bottom: BorderSide(color: AppColors.lineaSuave)),
        // Franja del color de la sección: orienta sin leer el título.
        gradient: LinearGradient(
          colors: [destino.fondo, AppColors.superficie],
          stops: const [0, 0.45],
        ),
      ),
      child: Row(
        children: [
          IconButton(
            tooltip: menuAbierto ? 'Ocultar menú' : 'Mostrar menú',
            icon: Icon(menuAbierto ? Icons.menu_open : Icons.menu),
            onPressed: alAlternarMenu,
          ),
          const SizedBox(width: 8),
          if (!menuAbierto) ...[
            const LogoApp(tamano: 30),
            const SizedBox(width: 16),
            Container(width: 1, height: 28, color: AppColors.linea),
            const SizedBox(width: 16),
          ],
          IconoEnRecuadro(
            icono: destino.iconoActivo,
            color: Colors.white,
            fondo: destino.color,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(destino.etiqueta, overflow: TextOverflow.ellipsis, style: tema.titleLarge),
                Text(
                  destino.descripcion,
                  overflow: TextOverflow.ellipsis,
                  style: tema.bodySmall?.copyWith(color: AppColors.tintaSuave),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _BarraLateral extends StatelessWidget {
  const _BarraLateral({required this.state, required this.sesion});
  final GoRouterState state;
  final SesionController sesion;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final activo = indiceDestino(state);
    final datos = sesion.sesion;
    final conNombre = datos?.nombreCompleto.isNotEmpty == true;
    final nombre = conNombre ? datos!.nombreCompleto : (datos?.correo ?? '');
    return Container(
      width: 248,
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [AppColors.bosqueClaro, AppColors.bosque],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(8, 0, 8, 28),
            child: Align(
              alignment: Alignment.centerLeft,
              child: LogoApp(sobreMarca: true, tamano: 32),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
            child: Text(
              'MENÚ',
              style: tema.labelSmall?.copyWith(
                color: Colors.white54,
                letterSpacing: 1.4,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          for (var i = 0; i < destinos.length; i++)
            _ItemMenu(
              destino: destinos[i],
              activo: i == activo,
              alTocar: () => context.go(destinos[i].ruta),
            ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Monograma(nombre: nombre, tamano: 36),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            nombre,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: tema.titleSmall?.copyWith(color: Colors.white),
                          ),
                          if (conNombre)
                            Text(
                              datos!.correo,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: tema.bodySmall?.copyWith(color: Colors.white70),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white,
                    side: const BorderSide(color: Colors.white24),
                  ),
                  onPressed: () => confirmarCierreDeSesion(context, sesion),
                  icon: const Icon(Icons.logout, size: 18),
                  label: const Text('Cerrar sesión'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ItemMenu extends StatelessWidget {
  const _ItemMenu({
    required this.destino,
    required this.activo,
    required this.alTocar,
  });

  final DestinoNavegacion destino;
  final bool activo;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    // Tono claro del color de la sección: legible sobre el verde oscuro.
    final claro = Color.lerp(destino.color, Colors.white, 0.55)!;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Semantics(
        button: true,
        selected: activo,
        child: Material(
          color: activo ? Colors.white.withValues(alpha: 0.14) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            hoverColor: Colors.white.withValues(alpha: 0.08),
            onTap: alTocar,
            child: Container(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
              decoration: BoxDecoration(
                border: Border(
                  left: BorderSide(
                    color: activo ? claro : Colors.transparent,
                    width: 4,
                  ),
                ),
              ),
              child: Row(
                children: [
                  IconoEnRecuadro(
                    icono: activo ? destino.iconoActivo : destino.icono,
                    color: activo ? destino.color : claro,
                    fondo: activo ? Colors.white : Colors.white.withValues(alpha: 0.10),
                    tamano: 34,
                  ),
                  const SizedBox(width: 12),
                  Flexible(
                    child: Text(
                      destino.etiqueta,
                      overflow: TextOverflow.ellipsis,
                      style: tema.bodyLarge?.copyWith(
                        fontWeight: activo ? FontWeight.w700 : FontWeight.w500,
                        color: activo ? Colors.white : Colors.white.withValues(alpha: 0.82),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
