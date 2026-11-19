import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../authentication/infraestructure/ui/login_page.dart';
import '../pagos/infraestructure/ui/historial_page.dart';
import '../pagos/infraestructure/ui/panel_compra.dart';
import '../perfil/infraestructure/ui/perfil_page.dart';
import '../productos/infraestructure/ui/catalogo_page.dart';
import 'dependencias.dart';
import 'shell_comun.dart';

/// Rutas de la aplicación. El control de acceso está en [redirect]: sin sesión
/// solo se puede ver el login; con sesión, el login redirige al catálogo.
GoRouter crearRouter(Dependencias deps, ConstructorShell shell) {
  return GoRouter(
    initialLocation: '/catalogo',
    refreshListenable: deps.sesion,
    redirect: (context, state) {
      final enLogin = state.uri.path == '/login';
      if (!deps.sesion.autenticado) return enLogin ? null : '/login';
      if (enLogin || state.uri.path == '/') return '/catalogo';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginPage(sesion: deps.sesion),
      ),
      ShellRoute(
        builder: shell,
        routes: [
          GoRoute(
            path: '/catalogo',
            builder: (context, state) => _catalogo(deps, null),
            routes: [
              GoRoute(
                path: ':clave',
                builder: (context, state) =>
                    _catalogo(deps, state.pathParameters['clave']),
              ),
            ],
          ),
          GoRoute(
            path: '/compras',
            builder: (context, state) => _historial(deps, null),
            routes: [
              GoRoute(
                path: ':upc',
                builder: (context, state) =>
                    _historial(deps, state.pathParameters['upc']),
              ),
            ],
          ),
          GoRoute(
            path: '/perfil',
            builder: (context, state) => PerfilPage(controller: deps.perfil),
          ),
          GoRoute(
            path: '/cuentas',
            builder: (context, state) => CuentasBancariasPage(controller: deps.perfil),
          ),
        ],
      ),
    ],
  );
}

Widget _catalogo(Dependencias deps, String? clave) {
  return Builder(
    builder: (context) => CatalogoPage(
      controller: deps.catalogo,
      claveSeleccionada: clave,
      alSeleccionar: (c) => context.go('/catalogo/$c'),
      construirAccionCompra: (context, producto) => PanelCompra(
        producto: producto,
        cuentas: deps.cuentas,
        pagos: deps.pagos,
        realizarCompra: deps.realizarCompra,
        alConfirmarCompra: deps.historial.recargar,
        alVerHistorial: () => context.go('/compras'),
      ),
    ),
  );
}

Widget _historial(Dependencias deps, String? upc) {
  return Builder(
    builder: (context) => HistorialPage(
      controller: deps.historial,
      referenciaSeleccionada: upc,
      alSeleccionar: (u) => context.go('/compras/$u'),
    ),
  );
}
