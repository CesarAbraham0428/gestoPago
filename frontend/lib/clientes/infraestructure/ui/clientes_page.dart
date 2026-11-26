import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/ui/ilustracion.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/notificaciones.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../application/clientes_controller.dart';
import '../../domain/entities/cliente_admin.dart';

/// Panel de clientes (solo administradores): buscador y filtros arriba y una
/// cuadrícula de cards con nombre, correo y estado. Al elegir una card se abre
/// el detalle completo, desde donde se activa o desactiva la cuenta.
class ClientesPage extends StatefulWidget {
  const ClientesPage({
    super.key,
    required this.controller,
    required this.idSeleccionado,
    required this.alSeleccionar,
    required this.alVolver,
  });

  final ClientesController controller;
  final int? idSeleccionado;
  final ValueChanged<int> alSeleccionar;
  final VoidCallback alVolver;

  @override
  State<ClientesPage> createState() => _ClientesPageState();
}

class _ClientesPageState extends State<ClientesPage> {
  final _busqueda = TextEditingController();

  @override
  void initState() {
    super.initState();
    // Cargar notifica a quien escucha al controlador (el menú); no puede ocurrir durante el build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.controller.esAdministrador != false) widget.controller.cargar();
    });
  }

  @override
  void dispose() {
    _busqueda.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        if (widget.controller.esAdministrador == false) {
          return const VistaMensaje(
            icono: Icons.lock_outline,
            color: AppColors.coral,
            fondo: AppColors.coralTenue,
            titulo: 'Solo para administradores',
            detalle: 'Tu cuenta no tiene permiso para administrar clientes.',
          );
        }
        final id = widget.idSeleccionado;
        if (id != null) {
          return _Detalle(
            controller: widget.controller,
            cliente: widget.controller.porId(id),
            alVolver: widget.alVolver,
          );
        }
        return _PanelLista(
          controller: widget.controller,
          busqueda: _busqueda,
          alSeleccionar: widget.alSeleccionar,
        );
      },
    );
  }
}

// ---------------------------------------------------------------- lista

class _PanelLista extends StatelessWidget {
  const _PanelLista({
    required this.controller,
    required this.busqueda,
    required this.alSeleccionar,
  });

  final ClientesController controller;
  final TextEditingController busqueda;
  final ValueChanged<int> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    final listo = controller.estado == EstadoClientes.listo;
    final buscador = TextField(
      controller: busqueda,
      onChanged: controller.buscar,
      textInputAction: TextInputAction.search,
      decoration: InputDecoration(
        isDense: true,
        hintText: 'Buscar por nombre, correo o RFC',
        prefixIcon: const Icon(Icons.search),
        suffixIcon: busqueda.text.isEmpty
            ? null
            : IconButton(
                tooltip: 'Borrar búsqueda',
                icon: const Icon(Icons.close),
                onPressed: () {
                  busqueda.clear();
                  controller.buscar('');
                },
              ),
      ),
    );
    final inactivos = controller.total - controller.activos;
    final chips = SizedBox(
      height: 44,
      child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          _FiltroEstado(
            etiqueta: listo ? 'Todos (${controller.total})' : 'Todos',
            activo: controller.filtro == FiltroEstadoCliente.todos,
            alTocar: () => controller.filtrar(FiltroEstadoCliente.todos),
          ),
          _FiltroEstado(
            etiqueta: listo ? 'Activos (${controller.activos})' : 'Activos',
            activo: controller.filtro == FiltroEstadoCliente.activos,
            alTocar: () => controller.filtrar(FiltroEstadoCliente.activos),
          ),
          _FiltroEstado(
            etiqueta: listo ? 'Inactivos ($inactivos)' : 'Inactivos',
            activo: controller.filtro == FiltroEstadoCliente.inactivos,
            alTocar: () => controller.filtrar(FiltroEstadoCliente.inactivos),
          ),
        ],
      ),
    );

    return RefreshIndicator(
      onRefresh: () => controller.cargar(forzar: true),
      child: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [buscador, const SizedBox(height: 8), chips],
              ),
            ),
          ),
          ..._contenido(context),
        ],
      ),
    );
  }

  List<Widget> _contenido(BuildContext context) {
    Widget relleno(Widget hijo) =>
        SliverFillRemaining(hasScrollBody: false, child: hijo);
    switch (controller.estado) {
      case EstadoClientes.inicial:
      case EstadoClientes.cargando:
        return [relleno(const VistaCargando(mensaje: 'Cargando clientes'))];
      case EstadoClientes.error:
        return [
          relleno(
            VistaMensaje(
              icono: Icons.cloud_off_outlined,
              color: AppColors.coral,
              fondo: AppColors.coralTenue,
              titulo: 'No pudimos cargar los clientes',
              detalle: controller.error ?? 'Revisa tu conexión e inténtalo de nuevo.',
              accion: () => controller.cargar(forzar: true),
            ),
          ),
        ];
      case EstadoClientes.listo:
        break;
    }
    final clientes = controller.visibles;
    if (clientes.isEmpty) {
      return [
        relleno(
          controller.total == 0
              ? const VistaMensaje(
                  icono: Icons.groups_outlined,
                  titulo: 'Aún no hay otros clientes',
                  detalle: 'Cuando alguien se registre, aparecerá aquí.',
                )
              : VistaMensaje(
                  icono: Icons.search_off,
                  titulo: 'Sin resultados',
                  detalle: 'Prueba con otro nombre o quita los filtros.',
                  etiquetaAccion: 'Quitar filtros',
                  accion: () {
                    busqueda.clear();
                    controller.buscar('');
                    controller.filtrar(FiltroEstadoCliente.todos);
                  },
                ),
        ),
      ];
    }
    // Con la letra del sistema grande la card necesita más alto.
    final alto = 200 * MediaQuery.textScalerOf(context).scale(1).clamp(1.0, 1.6);
    return [
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        sliver: SliverGrid.builder(
          gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 280,
            mainAxisExtent: alto,
            mainAxisSpacing: 14,
            crossAxisSpacing: 14,
          ),
          itemCount: clientes.length,
          itemBuilder: (context, i) => _TarjetaCliente(
            key: ValueKey(clientes[i].id),
            cliente: clientes[i],
            alTocar: () => alSeleccionar(clientes[i].id),
          ),
        ),
      ),
    ];
  }
}

class _FiltroEstado extends StatelessWidget {
  const _FiltroEstado({
    required this.etiqueta,
    required this.activo,
    required this.alTocar,
  });

  final String etiqueta;
  final bool activo;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(etiqueta),
        selected: activo,
        onSelected: (_) => alTocar(),
        side: BorderSide(color: activo ? AppColors.marca : AppColors.linea),
      ),
    );
  }
}

/// Color de la banda de una card: el del cliente si su cuenta está activa y un
/// gris si no, para que las cuentas inactivas se distingan de un vistazo.
Color _colorDe(ClienteAdmin c) =>
    c.activo ? Monograma.colorDe(c.nombre) : AppColors.tintaSuave;

class _TarjetaCliente extends StatefulWidget {
  const _TarjetaCliente({super.key, required this.cliente, required this.alTocar});

  final ClienteAdmin cliente;
  final VoidCallback alTocar;

  @override
  State<_TarjetaCliente> createState() => _TarjetaClienteState();
}

class _TarjetaClienteState extends State<_TarjetaCliente> {
  var _encima = false;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final c = widget.cliente;
    final color = _colorDe(c);
    return Semantics(
      button: true,
      child: AnimatedSlide(
        offset: Offset(0, _encima ? -0.015 : 0),
        duration: const Duration(milliseconds: 160),
        child: Material(
          color: AppColors.superficie,
          elevation: _encima ? 8 : 1,
          shadowColor: color.withValues(alpha: 0.35),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: _encima ? color.withValues(alpha: 0.4) : AppColors.lineaSuave,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: widget.alTocar,
            onHover: (v) => setState(() => _encima = v),
            hoverColor: color.withValues(alpha: 0.04),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  height: 92,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        Color.lerp(color, Colors.white, 0.86)!,
                        Color.lerp(color, Colors.white, 0.72)!,
                      ],
                    ),
                  ),
                  child: Stack(
                    children: [
                      Positioned(
                        right: -24,
                        bottom: -36,
                        child: _Circulo(color: color, tamano: 110),
                      ),
                      Positioned(
                        left: -16,
                        top: -30,
                        child: _Circulo(color: color, tamano: 70),
                      ),
                      Center(child: _AvatarPersona(color: color, tamano: 60)),
                      Positioned(
                        right: 10,
                        top: 10,
                        child: _PildoraEstado(activo: c.activo),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          c.nombre,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: tema.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          c.correo,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: tema.bodySmall,
                        ),
                        const Spacer(),
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                'Ver detalle',
                                style: tema.labelMedium?.copyWith(
                                  color: c.activo ? AppColors.marca : AppColors.tintaSuave,
                                ),
                              ),
                            ),
                            Icon(
                              Icons.arrow_forward,
                              size: 18,
                              color: c.activo ? AppColors.marca : AppColors.tintaSuave,
                            ),
                          ],
                        ),
                      ],
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

/// Círculo blanco con el icono de una persona.
class _AvatarPersona extends StatelessWidget {
  const _AvatarPersona({required this.color, required this.tamano});
  final Color color;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: Container(
        width: tamano,
        height: tamano,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.3),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Icon(Icons.person, color: color, size: tamano * 0.62),
      ),
    );
  }
}

class _PildoraEstado extends StatelessWidget {
  const _PildoraEstado({required this.activo});
  final bool activo;

  @override
  Widget build(BuildContext context) {
    final tono = activo ? AppColors.exito : AppColors.rechazo;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(activo ? Icons.check_circle : Icons.block, size: 13, color: tono),
          const SizedBox(width: 4),
          Text(
            activo ? 'Activa' : 'Inactiva',
            maxLines: 1,
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: tono),
          ),
        ],
      ),
    );
  }
}

class _Circulo extends StatelessWidget {
  const _Circulo({required this.color, required this.tamano});
  final Color color;
  final double tamano;

  @override
  Widget build(BuildContext context) => Container(
    width: tamano,
    height: tamano,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      color: color.withValues(alpha: 0.12),
    ),
  );
}

// -------------------------------------------------------------- detalle

class _Detalle extends StatelessWidget {
  const _Detalle({
    required this.controller,
    required this.cliente,
    required this.alVolver,
  });

  final ClientesController controller;
  final ClienteAdmin? cliente;
  final VoidCallback alVolver;

  @override
  Widget build(BuildContext context) {
    final c = cliente;
    if (c == null) {
      return controller.estado == EstadoClientes.cargando ||
              controller.estado == EstadoClientes.inicial
          ? const VistaCargando()
          : VistaMensaje(
              icono: Icons.search_off,
              titulo: 'No encontramos este cliente',
              detalle: 'Vuelve a la lista para elegir otro.',
              etiquetaAccion: 'Volver a clientes',
              accion: alVolver,
            );
    }
    final p = c.perfil;
    final d = p.domicilio;
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: alVolver,
                  icon: const Icon(Icons.arrow_back, size: 18),
                  label: const Text('Volver a clientes'),
                ),
              ),
              const SizedBox(height: 8),
              _Banner(controller: controller, cliente: c),
              const SizedBox(height: 20),
              _Seccion(
                icono: Icons.manage_accounts_outlined,
                titulo: 'Cuenta de usuario',
                color: AppColors.indigo,
                fondo: AppColors.indigoTenue,
                filas: {
                  'Estado': c.activo ? 'Activa' : 'Inactiva',
                  'Rol': c.esAdministrador ? 'Administrador' : 'Cliente',
                  'Cliente desde': c.fechaAlta == null ? null : formatoFecha(c.fechaAlta!),
                },
              ),
              _Seccion(
                icono: Icons.badge_outlined,
                titulo: 'Datos personales',
                color: AppColors.marca,
                fondo: AppColors.marcaTenue,
                filas: {
                  'Nombre': p.nombreCompleto,
                  'Fecha de nacimiento': formatoFecha(p.fechaNacimiento),
                  'Sexo': p.sexo,
                  'Estado civil': p.estadoCivil,
                  'Nacionalidad': p.nacionalidad,
                  'CURP': p.curp,
                  'RFC': p.rfc,
                },
              ),
              _Seccion(
                icono: Icons.contact_phone_outlined,
                titulo: 'Contacto',
                color: AppColors.turquesa,
                fondo: AppColors.turquesaTenue,
                filas: {
                  'Correo electrónico': p.correoElectronico,
                  'Teléfono móvil': formatoTelefono(p.telefonoMovil),
                  'Teléfono alternativo': p.telefonoAlternativo == null
                      ? null
                      : formatoTelefono(p.telefonoAlternativo!),
                },
              ),
              _Seccion(
                icono: Icons.work_outline,
                titulo: 'Empleo',
                color: AppColors.azul,
                fondo: AppColors.azulTenue,
                filas: {
                  'Ocupación': p.ocupacion,
                  'Empresa': p.empresa,
                  'Ingreso mensual': p.ingresoMensual == null
                      ? null
                      : formatoMoneda(p.ingresoMensual!),
                },
              ),
              _Seccion(
                icono: Icons.home_outlined,
                titulo: 'Domicilio',
                color: AppColors.ciruela,
                fondo: AppColors.ciruelaTenue,
                filas: {
                  'Calle y número': d?.lineaCalle,
                  'Colonia': d?.colonia,
                  'Código postal': d?.codigoPostal,
                  'Municipio': d?.municipio,
                  'Estado': d?.estado,
                  'País': d?.pais,
                },
              ),
              _SeccionCuentas(cliente: c),
            ],
          ),
        ),
      ),
    );
  }
}

/// Encabezado del detalle: persona, estado y el botón para activar o desactivar.
class _Banner extends StatelessWidget {
  const _Banner({required this.controller, required this.cliente});
  final ClientesController controller;
  final ClienteAdmin cliente;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final c = cliente;
    final cambiando = controller.cambiandoId == c.id;
    final avatar = _AvatarPersona(
      color: c.activo ? AppColors.marca : AppColors.tintaSuave,
      tamano: 72,
    );
    final textos = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          c.nombre,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: tema.titleLarge?.copyWith(
            color: Colors.white,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          c.correo,
          overflow: TextOverflow.ellipsis,
          style: tema.bodyMedium?.copyWith(color: Colors.white70),
        ),
        const SizedBox(height: 8),
        _PildoraEstado(activo: c.activo),
      ],
    );
    final boton = FilledButton.icon(
      style: FilledButton.styleFrom(
        backgroundColor: Colors.white,
        foregroundColor: c.activo ? AppColors.rechazo : AppColors.marca,
      ),
      onPressed: cambiando ? null : () => _alternarEstado(context, controller, c),
      icon: cambiando
          ? const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(c.activo ? Icons.block : Icons.check_circle_outline, size: 18),
      label: Text(c.activo ? 'Desactivar cuenta' : 'Reactivar cuenta'),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: c.activo
                ? const [AppColors.marca, AppColors.bosque]
                : const [Color(0xFF55625B), Color(0xFF2E3A33)],
          ),
        ),
        child: LayoutBuilder(
          builder: (context, restricciones) {
            if (restricciones.maxWidth < 520) {
              return Column(
                children: [
                  avatar,
                  const SizedBox(height: 12),
                  Align(alignment: Alignment.center, child: textos),
                  const SizedBox(height: 18),
                  SizedBox(width: double.infinity, child: boton),
                ],
              );
            }
            return Row(
              children: [
                avatar,
                const SizedBox(width: 18),
                Expanded(child: textos),
                const SizedBox(width: 12),
                boton,
              ],
            );
          },
        ),
      ),
    );
  }
}

/// Pide confirmación y cambia el estado de la cuenta; el resultado sale como aviso.
Future<void> _alternarEstado(
  BuildContext context,
  ClientesController controller,
  ClienteAdmin c,
) async {
  final activar = !c.activo;
  final nombre = c.perfil.primerNombre;
  final confirmado = await showDialog<bool>(
    context: context,
    builder: (dialogo) => AlertDialog(
      title: Text(
        activar ? '¿Reactivar la cuenta de $nombre?' : '¿Desactivar la cuenta de $nombre?',
      ),
      content: Text(
        activar
            ? 'Podrá iniciar sesión de nuevo y sus cuentas bancarias volverán a estar activas.'
            : 'No podrá iniciar sesión ni usar sus cuentas bancarias hasta que la reactives.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogo).pop(false),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          style: activar
              ? null
              : FilledButton.styleFrom(backgroundColor: AppColors.rechazo),
          onPressed: () => Navigator.of(dialogo).pop(true),
          child: Text(activar ? 'Reactivar' : 'Desactivar'),
        ),
      ],
    ),
  );
  if (confirmado != true) return;
  final error = await controller.cambiarEstado(c, activo: activar);
  if (error == null) {
    Notificaciones.exito(
      activar ? 'Cuenta reactivada' : 'Cuenta desactivada',
      detalle: c.nombre,
    );
  } else {
    Notificaciones.error('No pudimos cambiar el estado', detalle: error);
  }
}

class _Tarjeta extends StatelessWidget {
  const _Tarjeta({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.superficie,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.lineaSuave),
      ),
      child: child,
    );
  }
}

class _TituloSeccion extends StatelessWidget {
  const _TituloSeccion({
    required this.icono,
    required this.titulo,
    required this.color,
    required this.fondo,
  });
  final IconData icono;
  final String titulo;
  final Color color;
  final Color fondo;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        IconoEnRecuadro(icono: icono, color: color, fondo: fondo, tamano: 36),
        const SizedBox(width: 12),
        Text(titulo, style: Theme.of(context).textTheme.titleMedium),
      ],
    );
  }
}

/// Tarjeta con título y filas etiqueta/valor; un dato vacío se muestra como "—".
class _Seccion extends StatelessWidget {
  const _Seccion({
    required this.icono,
    required this.titulo,
    required this.filas,
    required this.color,
    required this.fondo,
  });
  final IconData icono;
  final String titulo;
  final Color color;
  final Color fondo;
  final Map<String, String?> filas;

  @override
  Widget build(BuildContext context) {
    return _Tarjeta(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TituloSeccion(icono: icono, titulo: titulo, color: color, fondo: fondo),
          const SizedBox(height: 12),
          for (final e in filas.entries)
            FilaDato(
              etiqueta: e.key,
              valor: (e.value?.trim().isNotEmpty ?? false) ? e.value! : '—',
            ),
        ],
      ),
    );
  }
}

class _SeccionCuentas extends StatelessWidget {
  const _SeccionCuentas({required this.cliente});
  final ClienteAdmin cliente;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final cuentas = cliente.perfil.cuentas;
    return _Tarjeta(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _TituloSeccion(
            icono: Icons.account_balance_outlined,
            titulo: 'Cuentas bancarias',
            color: AppColors.coral,
            fondo: AppColors.coralTenue,
          ),
          const SizedBox(height: 12),
          if (cuentas.isEmpty)
            Text(
              'Sin cuentas bancarias registradas.',
              style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
            ),
          for (final cuenta in cuentas)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SelectableText(
                          cuenta.numeroCuenta,
                          style: tema.titleSmall?.copyWith(fontFeatures: cifrasTabulares),
                        ),
                        Text(
                          'Saldo ${formatoMoneda(cuenta.saldo)}',
                          style: tema.bodySmall?.copyWith(fontFeatures: cifrasTabulares),
                        ),
                      ],
                    ),
                  ),
                  EtiquetaEstado(
                    texto: cuenta.activa ? 'Activa' : 'Inactiva',
                    tono: cuenta.activa ? Tono.exito : Tono.neutro,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
