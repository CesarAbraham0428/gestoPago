import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/error/mensajes_usuario.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/domain/ports/almacen_sesion.dart';
import 'package:frontend/authentication/domain/ports/autenticacion_gateway.dart';
import 'package:frontend/authentication/domain/validadores_credenciales.dart';
import 'package:frontend/authentication/infraestructure/adapters/api_autenticacion_gateway.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _desafio = DesafioVerificacion(
  desafioId: 'abc',
  correo: 'ana@example.com',
  vigencia: Duration(minutes: 5),
);

class _GatewayFalso implements AutenticacionGateway {
  bool hayOtraSesion = false;
  bool falloDeRed = false;
  int cierres = 0;
  final List<bool> peticionesDeReemplazo = [];

  @override
  Future<DesafioVerificacion> iniciarSesion({
    required String correo,
    required String password,
  }) async => _desafio;

  @override
  Future<Sesion> verificarCodigo({
    required DesafioVerificacion desafio,
    required String codigo,
    required bool cerrarSesionAnterior,
  }) async {
    peticionesDeReemplazo.add(cerrarSesionAnterior);
    if (hayOtraSesion && !cerrarSesionAnterior) {
      throw const SesionActivaException('Ya tienes una sesión activa');
    }
    return Sesion(
      token: 'jwt',
      correo: desafio.correo,
      nombreCompleto: 'Ana Pérez',
      expiraEn: DateTime.now().add(const Duration(hours: 1)),
    );
  }

  @override
  Future<void> cerrarSesion() async {
    cierres++;
    if (falloDeRed) throw const ConexionException();
  }

  /// Cada conexión al canal de avisos abre uno nuevo; la prueba decide qué llega.
  final List<StreamController<String>> canales = [];

  @override
  Stream<String> cierresDeSesion() {
    final canal = StreamController<String>();
    canales.add(canal);
    return canal.stream;
  }
}

class _AlmacenFalso implements AlmacenSesion {
  Sesion? guardada;

  @override
  Future<Sesion?> leer() async => guardada;

  @override
  Future<void> guardar(Sesion sesion) async => guardada = sesion;

  @override
  Future<void> borrar() async => guardada = null;
}

void main() {
  late _GatewayFalso gateway;
  late _AlmacenFalso almacen;
  late SesionController sesion;

  setUp(() {
    gateway = _GatewayFalso();
    almacen = _AlmacenFalso();
    sesion = SesionController(gateway: gateway, almacen: almacen);
  });

  tearDown(() => sesion.dispose());

  group('Acceso en dos pasos', () {
    test('las credenciales solo piden el código: no abren sesión', () async {
      final desafio = await sesion.solicitarCodigo(
        correo: ' ANA@Example.com ',
        password: 'Segura123!',
      );
      expect(desafio.desafioId, 'abc');
      expect(sesion.autenticado, isFalse);
    });

    test('el código correcto abre la sesión y la guarda', () async {
      await sesion.verificarCodigo(desafio: _desafio, codigo: '123456');
      expect(sesion.autenticado, isTrue);
      expect(sesion.token, 'jwt');
      expect(almacen.guardada?.correo, 'ana@example.com');
    });

    test('con otra sesión abierta pide confirmación y luego la reemplaza', () async {
      gateway.hayOtraSesion = true;
      await expectLater(
        sesion.verificarCodigo(desafio: _desafio, codigo: '123456'),
        throwsA(isA<SesionActivaException>()),
      );
      expect(sesion.autenticado, isFalse);

      await sesion.verificarCodigo(
        desafio: _desafio,
        codigo: '123456',
        cerrarSesionAnterior: true,
      );
      expect(sesion.autenticado, isTrue);
      expect(gateway.peticionesDeReemplazo, [false, true]);
    });
  });

  group('Cierre de sesión', () {
    test('invalida la sesión en el servidor y borra la local', () async {
      await sesion.verificarCodigo(desafio: _desafio, codigo: '123456');
      await sesion.cerrarSesion();
      expect(gateway.cierres, 1);
      expect(sesion.autenticado, isFalse);
      expect(almacen.guardada, isNull);
    });

    test('si el servidor no responde igual se cierra en este dispositivo', () async {
      await sesion.verificarCodigo(desafio: _desafio, codigo: '123456');
      gateway.falloDeRed = true;
      await sesion.cerrarSesion();
      expect(sesion.autenticado, isFalse);
      expect(almacen.guardada, isNull);
    });

    test('un 401 deja el mensaje del servidor para el login', () async {
      await sesion.verificarCodigo(desafio: _desafio, codigo: '123456');
      sesion.sesionInvalidada('Tu sesión se cerró porque iniciaste sesión en otro dispositivo');
      await Future<void>.delayed(Duration.zero);
      expect(sesion.autenticado, isFalse);
      expect(sesion.aviso, contains('otro dispositivo'));
    });
  });

  group('Avisos de cierre (Server-Sent Events)', () {
    late SesionController rapida;

    setUp(() async {
      rapida = SesionController(
        gateway: gateway,
        almacen: almacen,
        esperaReconexion: const Duration(milliseconds: 10),
      );
      await rapida.verificarCodigo(desafio: _desafio, codigo: '123456');
    });

    tearDown(() => rapida.dispose());

    Future<void> espera(int ms) => Future<void>.delayed(Duration(milliseconds: ms));

    test('al iniciar sesión se abre el canal de avisos', () {
      expect(gateway.canales, hasLength(1));
    });

    test('si otro dispositivo toma la sesión, se cierra al instante sin navegar', () async {
      gateway.canales.single.add('Tu sesión se cerró porque iniciaste sesión en otro dispositivo');
      await espera(0);
      expect(rapida.autenticado, isFalse);
      expect(rapida.aviso, contains('otro dispositivo'));
      expect(almacen.guardada, isNull);
    });

    test('un 401 al conectar cierra la sesión', () async {
      gateway.canales.single.addError(const ApiException(401, 'Tu sesión ya no está activa'));
      await espera(0);
      expect(rapida.autenticado, isFalse);
      expect(rapida.aviso, 'Tu sesión ya no está activa');
    });

    test('un fallo de red no cierra la sesión: reconecta con espera creciente', () async {
      gateway.canales.single.addError(const ConexionException());
      await espera(5);
      expect(gateway.canales, hasLength(1)); // aún esperando 20 ms
      await espera(30);
      expect(gateway.canales, hasLength(2));
      expect(rapida.autenticado, isTrue);
    });

    test('si el servidor renueva el canal, se reabre enseguida', () async {
      await gateway.canales.single.close();
      await espera(30);
      expect(gateway.canales, hasLength(2));
    });

    test('tras muchos fallos la espera deja de crecer', () async {
      for (var i = 0; i < 7; i++) {
        gateway.canales.last.addError(const ConexionException());
        await espera(10 * 33);
      }
      expect(gateway.canales, hasLength(8));
    });

    test('al volver a la app se reabre el canal de inmediato', () {
      rapida.reconectarAvisos();
      expect(gateway.canales, hasLength(2));
    });

    test('sin sesión no se reconecta', () async {
      await rapida.cerrarSesion();
      await gateway.canales.single.close();
      rapida.reconectarAvisos();
      await espera(30);
      expect(gateway.canales, hasLength(1));
    });
  });

  group('Restauración', () {
    test('una sesión vencida guardada no se restaura', () async {
      almacen.guardada = Sesion(
        token: 'viejo',
        correo: 'ana@example.com',
        nombreCompleto: '',
        expiraEn: DateTime.now().subtract(const Duration(minutes: 1)),
      );
      await sesion.restaurar();
      expect(sesion.autenticado, isFalse);
      expect(almacen.guardada, isNull);
      expect(sesion.aviso, isNotNull);
    });
  });

  group('ApiAutenticacionGateway', () {
    ApiAutenticacionGateway conRespuesta(int estado, Map<String, Object?> cuerpo) {
      final cliente = MockClient(
        (_) async => http.Response(
          jsonEncode(cuerpo),
          estado,
          headers: {'content-type': 'application/json; charset=utf-8'},
        ),
      );
      return ApiAutenticacionGateway(ApiClient(baseUrl: 'http://localhost', client: cliente));
    }

    test('el canal de avisos solo entrega los cierres de sesión', () async {
      late http.BaseRequest recibida;
      final cliente = MockClient((r) async {
        recibida = r;
        return http.Response(
          'event: conectado\ndata: ok\n\n: latido\n\n'
          'event: sesion_cerrada\ndata: Tu sesión se cerró\n\n',
          200,
          headers: {'content-type': 'text/event-stream; charset=utf-8'},
        );
      });
      final gateway = ApiAutenticacionGateway(
        ApiClient(baseUrl: 'http://localhost', client: cliente),
      );
      expect(await gateway.cierresDeSesion().toList(), ['Tu sesión se cerró']);
      expect(recibida.url.path, '/auth/sesion/eventos');
    });

    test('409 al verificar es una sesión activa por confirmar', () async {
      final gateway = conRespuesta(409, {'codigo': 1, 'mensaje': 'Ya tienes una sesión activa'});
      await expectLater(
        gateway.verificarCodigo(desafio: _desafio, codigo: '123456', cerrarSesionAnterior: false),
        throwsA(isA<SesionActivaException>()),
      );
    });

    test('401 al verificar muestra un mensaje claro y genérico', () async {
      final gateway = conRespuesta(401, {'codigo': 1, 'mensaje': 'Código de verificación incorrecto'});
      await expectLater(
        gateway.verificarCodigo(desafio: _desafio, codigo: '000000', cerrarSesionAnterior: false),
        throwsA(isA<ApiException>().having((e) => e.message, 'mensaje', MensajesUsuario.codigoIncorrecto)),
      );
    });

    test('demasiados códigos pide volver a iniciar sesión', () async {
      final gateway = conRespuesta(401, {'mensaje': 'Demasiados códigos incorrectos; inicia sesión de nuevo'});
      await expectLater(
        gateway.verificarCodigo(desafio: _desafio, codigo: '000000', cerrarSesionAnterior: false),
        throwsA(isA<ApiException>().having((e) => e.message, 'mensaje', MensajesUsuario.demasiadosCodigos)),
      );
    });

    test('el login no distingue correo inexistente de contraseña incorrecta', () async {
      for (final (estado, mensaje) in [(401, 'Usuario o contraseña incorrectos'), (404, 'Usuario no encontrado')]) {
        await expectLater(
          conRespuesta(estado, {'mensaje': mensaje})
              .iniciarSesion(correo: 'ana@example.com', password: 'Segura123!'),
          throwsA(isA<ApiException>()
              .having((e) => e.message, 'mensaje', MensajesUsuario.credencialesIncorrectas)),
        );
      }
      await expectLater(
        conRespuesta(403, {'mensaje': 'El usuario o cliente está inactivo'})
            .iniciarSesion(correo: 'ana@example.com', password: 'Segura123!'),
        throwsA(isA<ApiException>().having((e) => e.message, 'mensaje', MensajesUsuario.cuentaNoDisponible)),
      );
    });

    test('el paso 1 devuelve el desafío y nunca un token', () async {
      final gateway = conRespuesta(200, {
        'codigo': 0,
        'mensaje': 'ok',
        'datos': {'desafioId': 'xyz', 'expiraEnMs': 300000},
      });
      final desafio = await gateway.iniciarSesion(correo: 'ana@x.com', password: 'Segura123!');
      expect(desafio.desafioId, 'xyz');
      expect(desafio.vigencia, const Duration(minutes: 5));
    });

    test('una respuesta sin token no abre sesión', () async {
      final gateway = conRespuesta(200, {'codigo': 0, 'mensaje': 'ok', 'datos': {}});
      await expectLater(
        gateway.verificarCodigo(desafio: _desafio, codigo: '123456', cerrarSesionAnterior: false),
        throwsA(isA<ConexionException>()),
      );
    });
  });

  group('Validación del código', () {
    test('exige exactamente 6 dígitos', () {
      expect(ValidadoresCredenciales.codigo('123456'), isNull);
      expect(ValidadoresCredenciales.codigo(''), isNotNull);
      expect(ValidadoresCredenciales.codigo('12345'), isNotNull);
      expect(ValidadoresCredenciales.codigo('12a456'), isNotNull);
    });
  });
}
