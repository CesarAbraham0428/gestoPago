import 'package:flutter/material.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/ui/notificaciones.dart';
import '../../../core/ui/ilustracion.dart';
import '../../../core/ui/monograma.dart';
import '../../../core/ui/tarjeta_debito.dart';
import '../../../core/ui/widgets_estado.dart';
import '../../../pagos/domain/entities/cuenta.dart';
import '../../application/perfil_controller.dart';
import '../../domain/entities/perfil.dart';
import 'formulario_perfil.dart';

/// Carga el perfil al abrirse y muestra cargando / error / [construir].
class _ConPerfil extends StatefulWidget {
  const _ConPerfil({required this.controller, required this.construir});
  final PerfilController controller;
  final Widget Function(BuildContext context, Perfil perfil) construir;

  @override
  State<_ConPerfil> createState() => _ConPerfilState();
}

class _ConPerfilState extends State<_ConPerfil> {
  @override
  void initState() {
    super.initState();
    if (widget.controller.estado == EstadoPerfil.inicial)
      widget.controller.cargar();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final c = widget.controller;
        final perfil = c.perfil;
        if (perfil != null) return widget.construir(context, perfil);
        if (c.estado == EstadoPerfil.error) {
          return VistaMensaje(
            icono: Icons.cloud_off_outlined,
            color: AppColors.coral,
            fondo: AppColors.coralTenue,
            titulo: 'No pudimos cargar tu cuenta',
            detalle: c.error ?? '',
            accion: c.cargar,
          );
        }
        return const VistaCargando(mensaje: 'Cargando tu cuenta…');
      },
    );
  }
}

/// Contenido centrado con ancho máximo legible y desplazamiento vertical.
class _Lienzo extends StatelessWidget {
  const _Lienzo({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 880),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: children,
          ),
        ),
      ),
    );
  }
}

/// "Mi perfil": datos personales, contacto, empleo y domicilio, con edición.
class PerfilPage extends StatefulWidget {
  const PerfilPage({super.key, required this.controller});
  final PerfilController controller;

  @override
  State<PerfilPage> createState() => _PerfilPageState();
}

class _PerfilPageState extends State<PerfilPage> {
  bool _editando = false;

  @override
  Widget build(BuildContext context) {
    return _ConPerfil(
      controller: widget.controller,
      construir: (context, p) {
        if (_editando) {
          return _Lienzo(
            children: [
              FormularioPerfil(
                perfil: p,
                guardando: widget.controller.guardando,
                alCancelar: () => setState(() => _editando = false),
                alGuardar: (editado) async {
                  final error = await widget.controller.guardar(editado);
                  if (!mounted) return error;
                  if (error == null) {
                    setState(() => _editando = false);
                    Notificaciones.exito(
                      'Tus datos se guardaron.',
                      detalle: 'Tu perfil ya está actualizado.',
                    );
                  }
                  return error;
                },
              ),
            ],
          );
        }
        final d = p.domicilio;
        void editar() => setState(() => _editando = true);
        return _Lienzo(
          children: [
            _Encabezado(
              perfil: p,
              accion: FilledButton.icon(
                onPressed: editar,
                icon: const Icon(Icons.edit_outlined, size: 18),
                label: const Text('Editar perfil'),
              ),
            ),
            const SizedBox(height: 20),
            _Seccion(
              icono: Icons.badge_outlined,
              titulo: 'Datos personales',
              color: AppColors.indigo,
              fondo: AppColors.indigoTenue,
              alAgregar: editar,
              datos: {
                'Nombre completo': p.nombreCompleto,
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
              alAgregar: editar,
              datos: {
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
              alAgregar: editar,
              datos: {
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
              alAgregar: editar,
              datos: {
                'Calle y número': d?.lineaCalle,
                'Colonia': d?.colonia,
                'Código postal': d?.codigoPostal,
                'Municipio': d?.municipio,
                'Estado': d?.estado,
                'País': d?.pais,
              },
            ),
          ],
        );
      },
    );
  }
}

/// "Cuenta bancaria": cuenta del cliente con saldo y estado.
class CuentasBancariasPage extends StatelessWidget {
  const CuentasBancariasPage({super.key, required this.controller});
  final PerfilController controller;

  @override
  Widget build(BuildContext context) {
    return _ConPerfil(
      controller: controller,
      construir: (context, p) {
        return _Lienzo(
          children: [
            _Encabezado(
              perfil: p,
              accion: IconButton(
                tooltip: 'Actualizar saldos',
                onPressed: controller.cargar,
                icon: const Icon(Icons.refresh),
              ),
            ),
            const SizedBox(height: 20),
            if (p.cuentas.isEmpty)
              const VistaMensaje(
                icono: Icons.account_balance_outlined,
                color: AppColors.coral,
                fondo: AppColors.coralTenue,
                titulo: 'Sin cuentas',
                detalle: 'No tienes cuentas bancarias registradas.',
              )
            else ...[
              for (final c in p.cuentas) _TarjetaCuenta(cuenta: c),
            ],
          ],
        );
      },
    );
  }
}

class _Tarjeta extends StatelessWidget {
  const _Tarjeta({required this.child, this.margen = EdgeInsets.zero});
  final Widget child;
  final EdgeInsets margen;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: margen,
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

class _Encabezado extends StatelessWidget {
  const _Encabezado({required this.perfil, required this.accion});
  final Perfil perfil;
  final Widget accion;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    final nombre = Text(perfil.nombreCompleto, style: tema.titleLarge);
    final correo = Text(
      perfil.correoElectronico,
      style: tema.bodyMedium?.copyWith(color: AppColors.tintaSuave),
    );
    return _Tarjeta(
      child: LayoutBuilder(
        builder: (context, restricciones) {
          // En pantallas angostas el nombre no cabe junto al botón: se apila y centra.
          if (restricciones.maxWidth < 520) {
            return Column(
              children: [
                Monograma(nombre: perfil.nombreCompleto, tamano: 72),
                const SizedBox(height: 14),
                DefaultTextStyle.merge(
                  textAlign: TextAlign.center,
                  child: nombre,
                ),
                const SizedBox(height: 4),
                DefaultTextStyle.merge(
                  textAlign: TextAlign.center,
                  child: correo,
                ),
                const SizedBox(height: 18),
                SizedBox(width: double.infinity, child: accion),
              ],
            );
          }
          return Row(
            children: [
              Monograma(nombre: perfil.nombreCompleto, tamano: 56),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [nombre, const SizedBox(height: 2), correo],
                ),
              ),
              const SizedBox(width: 12),
              accion,
            ],
          );
        },
      ),
    );
  }
}

/// Tarjeta con título y pares etiqueta/valor en dos columnas si hay espacio.
class _Seccion extends StatelessWidget {
  const _Seccion({
    required this.icono,
    required this.titulo,
    required this.datos,
    required this.color,
    required this.fondo,
    required this.alAgregar,
  });
  final VoidCallback alAgregar;
  final IconData icono;
  final Color color;
  final Color fondo;
  final String titulo;

  /// Un valor nulo o en blanco se muestra como campo pendiente.
  final Map<String, String?> datos;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return _Tarjeta(
      margen: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconoEnRecuadro(
                icono: icono,
                color: color,
                fondo: fondo,
                tamano: 36,
              ),
              const SizedBox(width: 12),
              Text(titulo, style: tema.titleMedium),
            ],
          ),
          const SizedBox(height: 16),
          LayoutBuilder(
            builder: (context, restricciones) {
              final columnas = restricciones.maxWidth >= 560 ? 2 : 1;
              final ancho =
                  (restricciones.maxWidth - 24 * (columnas - 1)) / columnas;
              return Wrap(
                spacing: 24,
                runSpacing: 16,
                children: [
                  for (final e in datos.entries)
                    SizedBox(
                      width: ancho,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            e.key,
                            style: tema.bodySmall?.copyWith(
                              color: AppColors.tintaSuave,
                            ),
                          ),
                          const SizedBox(height: 2),
                          if (e.value == null || e.value!.trim().isEmpty)
                            _CampoPendiente(alTocar: alAgregar)
                          else
                            SelectableText(e.value!, style: tema.bodyLarge),
                        ],
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

/// Marcador de un dato que el usuario no llenó: invita a completarlo.
class _CampoPendiente extends StatelessWidget {
  const _CampoPendiente({required this.alTocar});
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return Semantics(
      button: true,
      label: 'Sin registrar. Toca para completar',
      child: Material(
        color: AppColors.papel,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
          side: const BorderSide(color: AppColors.linea),
        ),
        child: InkWell(
          onTap: alTocar,
          customBorder: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            child: Row(
              children: [
                const Icon(
                  Icons.add_circle_outline,
                  size: 18,
                  color: AppColors.marca,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Sin registrar',
                    style: tema.bodyMedium?.copyWith(
                      color: AppColors.tintaSuave,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ),
                Text(
                  'Completar',
                  style: tema.labelMedium?.copyWith(
                    color: AppColors.marca,
                    fontWeight: FontWeight.w700,
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

class _TarjetaCuenta extends StatelessWidget {
  const _TarjetaCuenta({required this.cuenta});
  final Cuenta cuenta;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context).textTheme;
    return _Tarjeta(
      margen: const EdgeInsets.only(bottom: 12),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Cuenta ${cuentaEnmascarada(cuenta.numeroCuenta)}',
                  style: tema.titleMedium,
                ),
              ),
              EtiquetaEstado(
                texto: cuenta.activa ? 'Activa' : 'Inactiva',
                tono: cuenta.activa ? Tono.exito : Tono.neutro,
              ),
            ],
          ),
          const SizedBox(height: 16),
          TarjetaDebito(
            numeroCuenta: cuenta.numeroCuenta,
            saldo: cuenta.saldo,
            activa: cuenta.activa,
          ),
        ],
      ),
    );
  }
}
