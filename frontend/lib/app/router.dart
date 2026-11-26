import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../authentication/infraestructure/ui/login_page.dart';
import '../authentication/infraestructure/ui/registro_page.dart';
import '../notificaciones/infraestructure/ui/notificaciones_page.dart';
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
      final enAcceso =
          state.uri.path == '/login' || state.uri.path == '/registro';
      if (!deps.sesion.autenticado) return enAcceso ? null : '/login';
      if (enAcceso || state.uri.path == '/') return '/catalogo';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginPage(
          sesion: deps.sesion,
          alCrearCuenta: () => context.go('/registro'),
        ),
      ),
      GoRoute(
        path: '/registro',
        builder: (context, state) => RegistroPage(
          registro: deps.registro,
          catalogos: deps.catalogos,
          alVolver: () => context.go('/login'),
        ),
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
          // Hermanas y con la misma llave de página: elegir un pago no crea otra pantalla,
          // así la lista conserva su estado y su desplazamiento en vez de recargarse.
          GoRoute(
            path: '/compras',
            pageBuilder: (context, state) => _paginaHistorial(deps, null),
          ),
          GoRoute(
            path: '/compras/:upc',
            pageBuilder: (context, state) =>
                _paginaHistorial(deps, state.pathParameters['upc']),
          ),
          GoRoute(
            path: '/notificaciones',
            builder: (context, state) => NotificacionesPage(
              controller: deps.notificaciones,
              alVerCompra: (upc) => context.go('/compras/$upc'),
            ),
          ),
          GoRoute(
            path: '/perfil',
            builder: (context, state) => PerfilPage(controller: deps.perfil, catalogos: deps.catalogos),
          ),
          GoRoute(
            path: '/cuentas',
            builder: (context, state) =>
                CuentasBancariasPage(controller: deps.perfil),
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
      alVolver: () => context.go('/catalogo'),
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

Page<void> _paginaHistorial(Dependencias deps, String? upc) {
  return NoTransitionPage(
    key: const ValueKey('compras'),
    child: _historial(deps, upc),
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
