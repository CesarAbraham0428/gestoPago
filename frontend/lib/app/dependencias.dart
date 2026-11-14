import 'package:flutter/widgets.dart';

import '../authentication/application/sesion_controller.dart';
import '../authentication/domain/ports/almacen_sesion.dart';
import '../authentication/domain/ports/autenticacion_gateway.dart';
import '../authentication/infraestructure/adapters/almacen_sesion_seguro.dart';
import '../authentication/infraestructure/adapters/api_autenticacion_gateway.dart';
import '../core/config/app_config.dart';
import '../core/network/api_client.dart';
import '../pagos/application/historial_controller.dart';
import '../pagos/application/realizar_compra.dart';
import '../pagos/domain/ports/cuentas_repository.dart';
import '../pagos/domain/ports/pagos_repository.dart';
import '../pagos/infraestructure/adapters/api_cuentas_repository.dart';
import '../pagos/infraestructure/adapters/api_pagos_repository.dart';
import '../productos/application/catalogo_controller.dart';
import '../productos/domain/ports/catalogo_repository.dart';
import '../productos/infraestructure/adapters/api_catalogo_repository.dart';

/// Raíz de composición: único lugar donde los puertos se conectan con sus
/// adaptadores concretos. Las dos aplicaciones usan el mismo contenedor.
class Dependencias {
  Dependencias._({
    required this.sesion,
    required this.catalogo,
    required this.historial,
    required this.cuentas,
    required this.pagos,
    required this.realizarCompra,
  });

  final SesionController sesion;
  final CatalogoController catalogo;
  final HistorialController historial;
  final CuentasRepository cuentas;
  final PagosRepository pagos;
  final RealizarCompra realizarCompra;

  /// Construye el grafo con los adaptadores HTTP y de almacenamiento reales.
  /// Los parámetros opcionales permiten sustituir adaptadores en pruebas.
  factory Dependencias.crear({
    String? apiBaseUrl,
    AutenticacionGateway? autenticacion,
    AlmacenSesion? almacenSesion,
    CatalogoRepository? catalogoRepositorio,
    PagosRepository? pagosRepositorio,
    CuentasRepository? cuentasRepositorio,
  }) {
    late final SesionController sesion;
    final api = ApiClient(
      baseUrl: apiBaseUrl ?? AppConfig.apiBaseUrl,
      tokenReader: () => sesion.token,
      onUnauthorized: (mensaje) => sesion.sesionInvalidada(mensaje),
    );
    sesion = SesionController(
      gateway: autenticacion ?? ApiAutenticacionGateway(api),
      almacen: almacenSesion ?? AlmacenSesionSeguro(),
    );
    final pagos = pagosRepositorio ?? ApiPagosRepository(api);
    final catalogo = CatalogoController(
      catalogoRepositorio ?? ApiCatalogoRepository(api),
    );
    final historial = HistorialController(pagos);

    // Al cerrar sesión se descartan los datos del usuario anterior.
    var estabaAutenticado = false;
    sesion.addListener(() {
      if (estabaAutenticado && !sesion.autenticado) {
        catalogo.limpiar();
        historial.limpiar();
      }
      estabaAutenticado = sesion.autenticado;
    });

    return Dependencias._(
      sesion: sesion,
      catalogo: catalogo,
      historial: historial,
      cuentas: cuentasRepositorio ?? ApiCuentasRepository(api),
      pagos: pagos,
      realizarCompra: RealizarCompra(pagos),
    );
  }
}

/// Expone [Dependencias] al árbol de widgets.
class DependenciasScope extends InheritedWidget {
  const DependenciasScope({
    super.key,
    required this.dependencias,
    required super.child,
  });

  final Dependencias dependencias;

  static Dependencias of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<DependenciasScope>();
    assert(scope != null, 'Falta DependenciasScope sobre este widget.');
    return scope!.dependencias;
  }

  @override
  bool updateShouldNotify(DependenciasScope oldWidget) =>
      dependencias != oldWidget.dependencias;
}
