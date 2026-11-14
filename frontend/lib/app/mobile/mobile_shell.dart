import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

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
    appBar: barraSuperior(context, state, sesion),
    body: ancho
        ? Row(
            children: [
              NavigationRail(
                backgroundColor: AppColors.superficie,
                selectedIndex: indiceDestino(state),
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
