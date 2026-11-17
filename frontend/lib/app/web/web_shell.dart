import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../authentication/application/sesion_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/ui/breakpoints.dart';
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
    return Scaffold(
      body: Row(
        children: [
          _BarraLateral(state: state, sesion: sesion),
          const VerticalDivider(width: 1),
          Expanded(
            child: Column(
              children: [
                _CabeceraContenido(
                  titulo: destinos[indiceDestino(state)].etiqueta,
                ),
                Expanded(child: contenido),
              ],
            ),
          ),
        ],
      ),
    );
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

class _CabeceraContenido extends StatelessWidget {
  const _CabeceraContenido({required this.titulo});
  final String titulo;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 64,
      alignment: Alignment.centerLeft,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      decoration: const BoxDecoration(
        color: AppColors.superficie,
        border: Border(bottom: BorderSide(color: AppColors.lineaSuave)),
      ),
      child: Text(titulo, style: Theme.of(context).textTheme.titleLarge),
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
    return Container(
      width: 248,
      color: AppColors.superficie,
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 0, 8, 24),
            child: Text(
              'GestoPago',
              style: tema.titleLarge?.copyWith(
                color: AppColors.marca,
                fontSize: 22,
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
          const Divider(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  datos?.nombreCompleto.isNotEmpty == true
                      ? datos!.nombreCompleto
                      : (datos?.correo ?? ''),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: tema.titleMedium,
                ),
                if (datos?.nombreCompleto.isNotEmpty == true)
                  Text(
                    datos!.correo,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: tema.bodySmall,
                  ),
              ],
            ),
          ),
          OutlinedButton.icon(
            onPressed: () => confirmarCierreDeSesion(context, sesion),
            icon: const Icon(Icons.logout, size: 18),
            label: const Text('Cerrar sesión'),
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
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Semantics(
        button: true,
        selected: activo,
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: alTocar,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            decoration: BoxDecoration(
              color: activo ? AppColors.marcaTenue : null,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Icon(
                  activo ? destino.iconoActivo : destino.icono,
                  size: 22,
                  color: activo ? AppColors.marcaOscura : AppColors.tintaSuave,
                ),
                const SizedBox(width: 12),
                Flexible(
                  child: Text(
                    destino.etiqueta,
                    overflow: TextOverflow.ellipsis,
                    style: tema.bodyLarge?.copyWith(
                      fontWeight: activo ? FontWeight.w700 : FontWeight.w500,
                      color: activo ? AppColors.marcaOscura : AppColors.tinta,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
