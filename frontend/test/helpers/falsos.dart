import 'package:flutter/material.dart';
import 'package:toastification/toastification.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/cuenta.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/domain/ports/cuentas_repository.dart';
import 'package:frontend/pagos/domain/ports/pagos_repository.dart';
import 'package:frontend/perfil/domain/entities/perfil.dart';
import 'package:frontend/perfil/domain/ports/perfil_repository.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/domain/ports/catalogo_repository.dart';

/// Monta [hijo] en un MaterialApp con el tema real y una pantalla de [tamano].
Future<void> montar(
  WidgetTester tester,
  Widget hijo, {
  Size tamano = const Size(500, 900),
}) async {
  tester.view.physicalSize = tamano;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ToastificationWrapper(
      child: MaterialApp(
        key: UniqueKey(), // fuerza a reconstruir el estado si se monta dos veces
        theme: AppTheme.light(),
        home: Scaffold(body: hijo),
      ),
    ),
  );
}

/// Deja pasar el tiempo de un toast abierto para que no quede un Timer pendiente.
Future<void> drenarToasts(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 5));
  await tester.pumpAndSettle();
}

const desafioPrueba = DesafioVerificacion(
  desafioId: 'd1',
  correo: 'ana@example.com',
  vigencia: Duration(minutes: 5),
);

Sesion sesionPrueba({Duration vigencia = const Duration(hours: 1)}) => Sesion(
  token: 'jwt',
  correo: 'ana@example.com',
  nombreCompleto: 'Ana Pérez',
  expiraEn: DateTime.now().add(vigencia),
);

class GatewayFalso implements AutenticacionGateway {
  Object? errorIniciar;
  Object? errorVerificar;
  int cierres = 0;
  final List<bool> reemplazos = [];

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async {
    if (errorIniciar != null) throw errorIniciar!; // ignore: only_throw_errors
    return desafioPrueba;
  }

  @override
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  }) async {
    reemplazos.add(cerrarSesionAnterior);
    if (errorVerificar != null) throw errorVerificar!; // ignore: only_throw_errors
    return sesionPrueba();
  }

  @override
  Future<void> cerrarSesion() async => cierres++;

  @override
  Stream<String> cierresDeSesion() => Stream<String>.multi((_) {});
}

class AlmacenFalso implements AlmacenSesion {
  Sesion? guardada;

  @override
  Future<Sesion?> leer() async => guardada;

  @override
  Future<void> guardar(Sesion sesion) async => guardada = sesion;

  @override
  Future<void> borrar() async => guardada = null;
}

class CuentasFalso implements CuentasRepository {
  CuentasFalso(this.alObtener);
  Future<List<Cuenta>> Function() alObtener;
  int llamadas = 0;

  @override
  Future<List<Cuenta>> obtenerActivas() {
    llamadas++;
    return alObtener();
  }
}

class PagosFalso implements PagosRepository {
  Future<Comprobante> Function(SolicitudCompra)? alPagar;
  Future<EstadoPago> Function(String)? alConsultar;
  Future<Pagina<CompraHistorial>> Function(int pagina)? alHistorial;
  int historiales = 0;

  @override
  Future<Comprobante> pagar(SolicitudCompra solicitud) => alPagar!(solicitud);

  @override
  Future<EstadoPago> consultarEstado(String upc) => alConsultar!(upc);

  @override
  Future<Pagina<CompraHistorial>> historial({
    required int pagina,
    required int tamanio,
  }) {
    historiales++;
    return alHistorial!(pagina);
  }
}

class CatalogoFalso implements CatalogoRepository {
  CatalogoFalso(this.alObtener);
  Future<List<Producto>> Function() alObtener;

  @override
  Future<List<Producto>> obtenerCatalogo() => alObtener();
}

Producto producto(
  String nombre,
  String servicio,
  int id, {
  int tipo = 1,
  double? precio = 50,
  int? cat,
  String? leyenda,
  String? tipoReferencia,
}) => Producto(
  nombre: nombre,
  servicio: servicio,
  idProducto: id,
  idServicio: 1,
  tipoFront: tipo,
  precio: precio,
  idCatTipoServicio: cat,
  leyenda: leyenda,
  tipoReferencia: tipoReferencia,
);

CompraHistorial compra(
  String upc, {
  String? producto = 'Recarga 50',
  String? servicio = 'Telcel',
  String? telefono,
  String? referencia,
  double monto = 50,
  double comision = 0,
  String estado = 'APROBADO',
}) => CompraHistorial(
  upc: upc,
  producto: producto,
  servicio: servicio,
  monto: monto,
  comision: comision,
  total: monto + comision,
  fecha: DateTime(2026, 3, 5, 14, 30),
  telefono: telefono,
  referencia: referencia,
  idTx: 77,
  numAutorizacion: 'AUT1',
  estado: estado,
);

Pagina<CompraHistorial> pagina(
  List<CompraHistorial> items, {
  int n = 0,
  int total = 1,
}) => Pagina(
  elementos: items,
  pagina: n,
  totalPaginas: total,
  totalElementos: items.length,
);

class PerfilFalso implements PerfilRepository {
  PerfilFalso([Perfil? perfil]) : perfil = perfil ?? perfilPrueba();
  Perfil perfil;
  Object? errorObtener;
  Object? errorActualizar;
  final List<Map<String, Object?>> cambiosRecibidos = [];
  Object? errorPin;
  final List<({String cuenta, String? actual, String nuevo})> pinesRecibidos = [];

  @override
  Future<Perfil> obtener() async {
    if (errorObtener != null) throw errorObtener!; // ignore: only_throw_errors
    return perfil;
  }

  @override
  Future<void> cambiarPin({
    required String numeroCuenta,
    String? pinActual,
    required String pinNuevo,
  }) async {
    pinesRecibidos.add((cuenta: numeroCuenta, actual: pinActual, nuevo: pinNuevo));
    if (errorPin != null) throw errorPin!; // ignore: only_throw_errors
  }

  @override
  Future<Perfil> actualizar({
    required String rfc,
    required Map<String, Object?> cambios,
  }) async {
    cambiosRecibidos.add(cambios);
    if (errorActualizar != null) throw errorActualizar!; // ignore: only_throw_errors
    return perfil = perfilPrueba(empresa: cambios['empresa'] as String? ?? perfil.empresa ?? 'Acme');
  }
}

Perfil perfilPrueba({String empresa = 'Acme', String? telefonoAlternativo}) => Perfil(
  primerNombre: 'Ana',
  apellidoPaterno: 'Pérez',
  apellidoMaterno: 'López',
  fechaNacimiento: DateTime(1995, 4, 12),
  curp: 'PELA950412MDFRPN09',
  rfc: 'PELA950412AB1',
  sexo: 'Femenino',
  nacionalidad: 'Mexicana',
  estadoCivil: 'Union libre',
  correoElectronico: 'ana@example.com',
  telefonoMovil: '5512345678',
  telefonoAlternativo: telefonoAlternativo,
  ocupacion: 'Ingeniera',
  empresa: empresa,
  ingresoMensual: 25000,
  domicilio: const Domicilio(
    calle: 'Reforma',
    numeroExterior: '10',
    colonia: 'Centro',
    municipio: 'Cuauhtémoc',
    estado: 'CDMX',
    codigoPostal: '06000',
    pais: 'México',
  ),
  cuentas: const [
    Cuenta(id: 1, numeroCuenta: '123456789012345678', saldo: 900, activa: true),
    Cuenta(id: 2, numeroCuenta: '876543210987654321', saldo: 50, activa: false),
  ],
);
