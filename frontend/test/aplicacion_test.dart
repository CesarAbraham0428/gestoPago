import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/pagos/application/historial_controller.dart';
import 'package:frontend/pagos/domain/entities/compra_historial.dart';
import 'package:frontend/pagos/domain/entities/comprobante.dart';
import 'package:frontend/pagos/domain/entities/estado_pago.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/domain/ports/pagos_repository.dart';
import 'package:frontend/productos/application/catalogo_controller.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/domain/ports/catalogo_repository.dart';

const _desafio = DesafioVerificacion(
  desafioId: 'd',
  correo: 'ana@example.com',
  vigencia: Duration(minutes: 5),
);

Sesion _sesion({Duration vigencia = const Duration(hours: 1)}) => Sesion(
  token: 'jwt',
  correo: 'ana@example.com',
  nombreCompleto: 'Ana',
  expiraEn: DateTime.now().add(vigencia),
);

class _Gateway implements AutenticacionGateway {
  _Gateway({this.fallaCierre = false});
  final bool fallaCierre;
  int cierres = 0;
  String? ultimoCorreo;

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async {
    ultimoCorreo = correo;
    return _desafio;
  }

  @override
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  }) async => _sesion();

  @override
  Future<void> cerrarSesion() async {
    cierres++;
    if (fallaCierre) throw const ConexionException();
  }
}

class _Almacen implements AlmacenSesion {
  _Almacen({this.guardada, this.fallaLeer = false, this.fallaEscribir = false});
  Sesion? guardada;
  final bool fallaLeer;
  final bool fallaEscribir;
  int borrados = 0;

  @override
  Future<Sesion?> leer() async {
    if (fallaLeer) throw StateError('sin almacén');
    return guardada;
  }

  @override
  Future<void> guardar(Sesion sesion) async {
    if (fallaEscribir) throw StateError('sin almacén');
    guardada = sesion;
  }

  @override
  Future<void> borrar() async {
    borrados++;
    if (fallaEscribir) throw StateError('sin almacén');
    guardada = null;
  }
}

class _Pagos implements PagosRepository {
  _Pagos(this.paginas);
  final List<Object> paginas; // Pagina o excepción
  final List<int> pedidas = [];

  @override
  Future<Pagina<CompraHistorial>> historial({
    required int pagina,
    required int tamanio,
  }) async {
    pedidas.add(pagina);
    final siguiente = paginas.removeAt(0);
    if (siguiente is Pagina<CompraHistorial>) return siguiente;
    throw siguiente; // ignore: only_throw_errors
  }

  @override
  Future<Comprobante> pagar(SolicitudCompra solicitud) =>
      throw UnimplementedError();

  @override
  Future<EstadoPago> consultarEstado(String upc) => throw UnimplementedError();
}

CompraHistorial _compra(String upc) => CompraHistorial(
  upc: upc,
  monto: 10,
  comision: 0,
  total: 10,
  fecha: DateTime(2026),
);

Pagina<CompraHistorial> _pagina(int n, int total, List<String> upcs) =>
    Pagina(
      elementos: upcs.map(_compra).toList(),
      pagina: n,
      totalPaginas: total,
      totalElementos: 99,
    );

class _Catalogo implements CatalogoRepository {
  _Catalogo(this.resultado);
  Object resultado;
  int llamadas = 0;

  @override
  Future<List<Producto>> obtenerCatalogo() async {
    llamadas++;
    final r = resultado;
    if (r is List<Producto>) return r;
    throw r; // ignore: only_throw_errors
  }
}

Producto _p(String nombre, String servicio, int id, {int tipo = 1, double? precio = 50, int? cat}) =>
    Producto(
      nombre: nombre,
      servicio: servicio,
      idProducto: id,
      idServicio: 1,
      tipoFront: tipo,
      precio: precio,
      idCatTipoServicio: cat,
    );

void main() {
  group('SesionController', () {
    test('restaurar activa una sesión vigente', () async {
      final c = SesionController(
        gateway: _Gateway(),
        almacen: _Almacen(guardada: _sesion()),
      );
      expect(c.restaurada, isFalse);
      await c.restaurar();
      expect(c.restaurada, isTrue);
      expect(c.autenticado, isTrue);
      expect(c.token, 'jwt');
      expect(c.sesion?.correo, 'ana@example.com');
      c.dispose();
    });

    test('restaurar con sesión vencida la borra y deja un aviso', () async {
      final almacen = _Almacen(
        guardada: _sesion(vigencia: const Duration(seconds: -5)),
      );
      final c = SesionController(gateway: _Gateway(), almacen: almacen);
      await c.restaurar();
      expect(c.autenticado, isFalse);
      expect(c.aviso, contains('expiró'));
      expect(almacen.borrados, 1);
      c.dispose();
    });

    test('restaurar sin sesión guardada o con almacén roto', () async {
      final vacio = SesionController(gateway: _Gateway(), almacen: _Almacen());
      await vacio.restaurar();
      expect(vacio.autenticado, isFalse);
      expect(vacio.aviso, isNull);
      expect(vacio.token, isNull);

      final roto = SesionController(
        gateway: _Gateway(),
        almacen: _Almacen(fallaLeer: true),
      );
      await roto.restaurar();
      expect(roto.restaurada, isTrue);
      expect(roto.autenticado, isFalse);
    });

    test('solicitarCodigo normaliza el correo', () async {
      final g = _Gateway();
      final c = SesionController(gateway: g, almacen: _Almacen());
      final d = await c.solicitarCodigo(
        correo: '  ANA@Example.com ',
        password: 'x',
      );
      expect(g.ultimoCorreo, 'ana@example.com');
      expect(d.desafioId, 'd');
    });

    test('verificarCodigo sobrevive a un almacén que falla', () async {
      final c = SesionController(
        gateway: _Gateway(),
        almacen: _Almacen(fallaEscribir: true),
      );
      await c.verificarCodigo(desafio: _desafio, codigo: ' 123456 ');
      expect(c.autenticado, isTrue);
      expect(c.aviso, isNull);
      c.dispose();
    });

    test('cerrarSesion: sin sesión no hace nada; con fallo de red igual cierra', () async {
      final g = _Gateway(fallaCierre: true);
      final c = SesionController(gateway: g, almacen: _Almacen());
      await c.cerrarSesion();
      expect(g.cierres, 0);

      await c.verificarCodigo(desafio: _desafio, codigo: '123456');
      await c.cerrarSesion(aviso: 'Adiós');
      expect(g.cierres, 1);
      expect(c.autenticado, isFalse);
      expect(c.aviso, 'Adiós');
    });

    test('cerrarSesion con almacén que falla no lanza', () async {
      final c = SesionController(
        gateway: _Gateway(),
        almacen: _Almacen(fallaEscribir: true),
      );
      await c.verificarCodigo(desafio: _desafio, codigo: '123456');
      await c.cerrarSesion();
      expect(c.autenticado, isFalse);
    });

    test('cerrarSesion simultáneo se ignora mientras se cierra', () async {
      final g = _Gateway();
      final c = SesionController(gateway: g, almacen: _Almacen());
      await c.verificarCodigo(desafio: _desafio, codigo: '123456');
      final a = c.cerrarSesion();
      final b = c.cerrarSesion();
      await Future.wait([a, b]);
      expect(g.cierres, 1);
    });

    test('sesionInvalidada cierra con el mensaje; sin sesión se ignora', () async {
      final c = SesionController(gateway: _Gateway(), almacen: _Almacen());
      c.sesionInvalidada('nada');
      expect(c.aviso, isNull);

      await c.verificarCodigo(desafio: _desafio, codigo: '123456');
      c.sesionInvalidada('Sesión reemplazada');
      await Future<void>.delayed(Duration.zero);
      expect(c.autenticado, isFalse);
      expect(c.aviso, 'Sesión reemplazada');
    });

    testWidgets('la sesión se cierra sola al vencer', (tester) async {
      final c = SesionController(
        gateway: _Gateway(),
        almacen: _Almacen(guardada: _sesion(vigencia: const Duration(seconds: 30))),
      );
      await c.restaurar();
      expect(c.autenticado, isTrue);
      await tester.pump(const Duration(seconds: 31));
      expect(c.autenticado, isFalse);
      expect(c.aviso, contains('expiró por seguridad'));
      c.dispose();
    });
  });

  group('HistorialController', () {
    test('recargar, cargar más y buscar por referencia', () async {
      final repo = _Pagos([
        _pagina(0, 2, ['A', 'B']),
        _pagina(1, 2, ['C']),
      ]);
      final c = HistorialController(repo);
      expect(c.estado, EstadoHistorial.inicial);
      await c.cargarMas(); // aún no hay nada cargado: se ignora
      expect(repo.pedidas, isEmpty);

      await c.recargar();
      expect(c.estado, EstadoHistorial.listo);
      expect(c.compras.map((e) => e.upc), ['A', 'B']);
      expect(c.hayMas, isTrue);
      expect(c.porReferencia('B')?.upc, 'B');
      expect(c.porReferencia('Z'), isNull);

      await c.cargarMas();
      expect(c.compras.length, 3);
      expect(c.hayMas, isFalse);
      expect(c.cargandoMas, isFalse);
      await c.cargarMas(); // ya no hay más
      expect(repo.pedidas, [0, 1]);
    });

    test('errores al recargar y al cargar más', () async {
      final c = HistorialController(
        _Pagos([
          const ApiException(500, 'Falla del servidor'),
          _pagina(0, 2, ['A']),
          const ConexionException('Sin red'),
          StateError('raro'),
          StateError('raro'),
          StateError('raro'),
        ]),
      );
      await c.recargar();
      expect(c.estado, EstadoHistorial.error);
      expect(c.error, 'Falla del servidor');

      await c.recargar();
      expect(c.estado, EstadoHistorial.listo);
      expect(c.error, isNull);

      await c.cargarMas();
      expect(c.errorMas, 'Sin red');
      await c.cargarMas();
      expect(c.errorMas, contains('más compras'));

      await c.recargar(); // lanza StateError genérico
      expect(c.estado, EstadoHistorial.error);
      expect(c.error, contains('historial'));
    });

    test('recargar se ignora si ya está cargando y limpiar reinicia', () async {
      final repo = _Pagos([
        _pagina(0, 1, ['A']),
      ]);
      final c = HistorialController(repo);
      final a = c.recargar();
      final b = c.recargar();
      await Future.wait([a, b]);
      expect(repo.pedidas, [0]);
      c.limpiar();
      expect(c.estado, EstadoHistorial.inicial);
      expect(c.compras, isEmpty);
      expect(c.hayMas, isFalse);
      expect(c.errorMas, isNull);
    });
  });

  group('CatalogoController', () {
    final productos = [
      _p('Recarga 50', 'Telcel', 1),
      _p('Recarga 100', 'AT&T', 2),
      _p('Luz', 'CFE', 3, tipo: 2, precio: 5),
      _p('Gift card', 'Amazon', 4, cat: 10),
      _p('Sin precio', 'Ñandú', 5, precio: null),
    ];

    test('carga, filtros, búsqueda sin acentos y orden', () async {
      final repo = _Catalogo(productos);
      final c = CatalogoController(repo);
      await c.cargar();
      expect(c.estado, EstadoCatalogo.listo);
      expect(c.total, 5);
      expect(c.visibles.map((p) => p.servicio), ['Amazon', 'AT&T', 'CFE', 'Telcel']);
      expect(c.soloDisponibles, isTrue);
      expect(c.porClave('1-1')?.nombre, 'Recarga 50');
      expect(c.porClave('9-9'), isNull);
      expect(c.categoriasPresentes, [
        CategoriaProducto.recargas,
        CategoriaProducto.tarjetas,
        CategoriaProducto.servicios,
      ]);

      await c.cargar(); // ya cargado: no vuelve a pedir
      expect(repo.llamadas, 1);
      await c.cargar(forzar: true);
      expect(repo.llamadas, 2);

      c.buscar('  TELCÉL ');
      expect(c.visibles.length, 1);
      expect(c.busqueda, '  TELCÉL ');
      c.buscar('nandu');
      expect(c.visibles, isEmpty); // no disponible oculto
      c.alternarSoloDisponibles(false);
      expect(c.visibles.single.servicio, 'Ñandú');

      c.buscar('');
      c.filtrarCategoria(CategoriaProducto.otros);
      expect(c.categoria, CategoriaProducto.otros);
      expect(c.visibles.single.nombre, 'Sin precio');
      c.alternarSoloDisponibles(true); // "otros" ya no existe: se limpia
      expect(c.categoria, isNull);

      c.filtrarCategoria(CategoriaProducto.recargas);
      c.alternarSoloDisponibles(true); // la categoría sigue presente
      expect(c.categoria, CategoriaProducto.recargas);
      expect(c.visibles.length, 2);
    });

    test('errores y limpiar', () async {
      final repo = _Catalogo(const ApiException(503, 'Caído'));
      final c = CatalogoController(repo);
      await c.cargar();
      expect(c.estado, EstadoCatalogo.error);
      expect(c.error, 'Caído');

      repo.resultado = StateError('raro');
      await c.cargar();
      expect(c.error, contains('catálogo'));

      repo.resultado = productos;
      await c.cargar();
      expect(c.estado, EstadoCatalogo.listo);
      c.limpiar();
      expect(c.estado, EstadoCatalogo.inicial);
      expect(c.visibles, isEmpty);
      expect(c.total, 0);
    });

    test('cargar se ignora mientras ya está cargando', () async {
      final repo = _Catalogo(productos);
      final c = CatalogoController(repo);
      final a = c.cargar();
      final b = c.cargar();
      await Future.wait([a, b]);
      expect(repo.llamadas, 1);
    });

    test('productos con igual servicio se ordenan por nombre', () async {
      final c = CatalogoController(
        _Catalogo([_p('Zeta', 'Mismo', 1), _p('Alfa', 'mismo', 2)]),
      );
      await c.cargar();
      expect(c.visibles.map((p) => p.nombre), ['Alfa', 'Zeta']);
    });
  });
}
