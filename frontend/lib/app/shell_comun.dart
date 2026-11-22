import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../authentication/application/sesion_controller.dart';
import '../core/theme/app_colors.dart';

/// Construye el marco de navegación (menú, barra superior) alrededor de la
/// pantalla activa. La web y el móvil aportan implementaciones distintas.
typedef ConstructorShell = Widget Function(
  BuildContext context,
  GoRouterState state,
  Widget contenido,
);

class DestinoNavegacion {
  const DestinoNavegacion({
    required this.ruta,
    required this.etiqueta,
    required this.icono,
    required this.iconoActivo,
    required this.color,
    required this.fondo,
    required this.descripcion,
    this.etiquetaCorta,
  });

  final String ruta;
  final String etiqueta;

  /// Versión de una sola palabra para la barra inferior móvil, donde no cabe la larga.
  final String? etiquetaCorta;
  final IconData icono;
  final IconData iconoActivo;
  final Color color;
  final Color fondo;
  final String descripcion;
}

const destinos = [
  DestinoNavegacion(
    ruta: '/catalogo',
    etiqueta: 'Catálogo',
    icono: Icons.storefront_outlined,
    iconoActivo: Icons.storefront,
    color: AppColors.turquesa,
    fondo: AppColors.turquesaTenue,
    descripcion: 'Recargas, servicios y tarjetas de regalo',
  ),
  DestinoNavegacion(
    ruta: '/compras',
    etiqueta: 'Mis compras',
    icono: Icons.receipt_long_outlined,
    iconoActivo: Icons.receipt_long,
    color: AppColors.azul,
    fondo: AppColors.azulTenue,
    descripcion: 'Tus recibos y pagos anteriores',
  ),
  DestinoNavegacion(
    ruta: '/perfil',
    etiqueta: 'Mi perfil',
    icono: Icons.person_outline,
    iconoActivo: Icons.person,
    color: AppColors.ciruela,
    fondo: AppColors.ciruelaTenue,
    descripcion: 'Datos personales, empleo y domicilio',
  ),
  DestinoNavegacion(
    ruta: '/cuentas',
    etiqueta: 'Cuenta bancaria',
    etiquetaCorta: 'Cuenta',
    icono: Icons.account_balance_outlined,
    iconoActivo: Icons.account_balance,
    color: AppColors.coral,
    fondo: AppColors.coralTenue,
    descripcion: 'Saldo y estado de tu cuenta',
  ),
];

int indiceDestino(GoRouterState state) {
  final ruta = state.uri.path;
  final i = destinos.indexWhere((d) => ruta == d.ruta || ruta.startsWith('${d.ruta}/'));
  return i < 0 ? 0 : i;
}

/// Una ruta de detalle tiene un segundo segmento: /catalogo/123-45.
bool esDetalle(GoRouterState state) => state.uri.pathSegments.length > 1;

String tituloPantalla(GoRouterState state) {
  final base = destinos[indiceDestino(state)];
  if (!esDetalle(state)) return base.etiqueta;
  return base.ruta == '/catalogo' ? 'Producto' : 'Compra';
}

Future<void> confirmarCierreDeSesion(
  BuildContext context,
  SesionController sesion,
) async {
  final confirmado = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: const Text('¿Cerrar sesión?'),
      content: const Text('Tendrás que iniciar sesión de nuevo para comprar.'),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('Cerrar sesión'),
        ),
      ],
    ),
  );
  if (confirmado == true) await sesion.cerrarSesion();
}

/// Menú de usuario para barras superiores: nombre, correo y cierre de sesión.
class MenuUsuario extends StatelessWidget {
  const MenuUsuario({super.key, required this.sesion});
  final SesionController sesion;

  @override
  Widget build(BuildContext context) {
    final datos = sesion.sesion;
    final tema = Theme.of(context).textTheme;
    return PopupMenuButton<String>(
      tooltip: 'Tu cuenta',
      icon: const Icon(Icons.account_circle_outlined),
      onSelected: (_) => confirmarCierreDeSesion(context, sesion),
      itemBuilder: (_) => [
        PopupMenuItem<String>(
          enabled: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                datos?.nombreCompleto.isNotEmpty == true ? datos!.nombreCompleto : 'Tu cuenta',
                style: tema.titleMedium,
              ),
              Text(datos?.correo ?? '', style: tema.bodySmall),
            ],
          ),
        ),
        const PopupMenuDivider(),
        const PopupMenuItem<String>(
          value: 'salir',
          child: Row(
            children: [
              Icon(Icons.logout, size: 20, color: AppColors.tintaSuave),
              SizedBox(width: 12),
              Text('Cerrar sesión'),
            ],
          ),
        ),
      ],
    );
  }
}

/// Barra superior común: título de la sección o flecha de regreso en detalles.
PreferredSizeWidget barraSuperior(
  BuildContext context,
  GoRouterState state,
  SesionController sesion, {
  Widget? menu,
}) {
  final detalle = esDetalle(state);
  return AppBar(
    automaticallyImplyLeading: false,
    leading: detalle
        ? IconButton(
            tooltip: 'Volver',
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.go(destinos[indiceDestino(state)].ruta),
          )
        : null,
    title: Text(tituloPantalla(state)),
    actions: [menu ?? MenuUsuario(sesion: sesion), const SizedBox(width: 4)],
  );
}
