import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/config/app_config.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/format/formatters.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/ui/breakpoints.dart';
import 'package:frontend/core/ui/monograma.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

http.Response _json(Object cuerpo, [int estado = 200]) => http.Response(
  jsonEncode(cuerpo),
  estado,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('formatters', () {
    test('moneda, fecha y fecha con hora', () {
      expect(formatoMoneda(1234.5), contains('1,234.50'));
      expect(formatoFecha(DateTime(2026, 3, 5)), contains('2026'));
      expect(formatoFechaHora(DateTime(2026, 3, 5, 9, 7)), contains('09:07'));
    });

    test('teléfono: formatea 10 dígitos y deja lo demás igual', () {
      expect(formatoTelefono('5512345678'), '55 1234 5678');
      expect(formatoTelefono('123'), '123');
    });

    test('cuenta enmascarada', () {
      final oculta = cuentaEnmascarada('1234567890123456');
      expect(oculta, endsWith('3456'));
      expect(oculta, isNot(contains('1234')));
      expect(cuentaEnmascarada('123'), '123');
    });

    test('parseMonto', () {
      expect(parseMonto('125.5'), 125.5);
      expect(parseMonto(' 125,50 '), 125.5);
      expect(parseMonto('1.234'), isNull);
      expect(parseMonto('-3'), isNull);
      expect(parseMonto(''), isNull);
      expect(parseMonto('abc'), isNull);
    });
  });

  group('AppException', () {
    test('mensajes, toString y sesión inválida', () {
      const api = ApiException(401, 'No');
      expect(api.esSesionInvalida, isTrue);
      expect(const ApiException(500, 'x').esSesionInvalida, isFalse);
      expect(api.toString(), 'No');
      expect(const SesionActivaException('otra').message, 'otra');
      expect(const ConexionException().message, contains('comunicarnos'));
      expect(const ConexionException('caída').toString(), 'caída');
    });
  });

  group('AppConfig', () {
    test('valores por omisión', () {
      expect(AppConfig.apiBaseUrl, 'http://localhost:8080');
      expect(AppConfig.esConexionSegura, isFalse);
      expect(AppConfig.conexionPermitida, isTrue);
      expect(AppConfig.inactividadMaxima, const Duration(minutes: 15));
      expect(
        AppConfig.tiempoEsperaPago,
        greaterThan(AppConfig.tiempoEsperaConsulta),
      );
    });
  });

  group('ApiClient', () {
    test('GET con token, query y datos desenvueltos', () async {
      late http.BaseRequest recibida;
      final api = ApiClient(
        baseUrl: 'http://api',
        tokenReader: () => 'jwt',
        client: MockClient((r) async {
          recibida = r;
          return _json({
            'mensaje': 'ok',
            'datos': {'a': 1},
          });
        }),
      );
      final r = await api.get('/x', query: {'p': '1'});
      expect(recibida.url.toString(), 'http://api/x?p=1');
      expect(recibida.headers['Authorization'], 'Bearer jwt');
      expect(recibida.headers.containsKey('Content-Type'), isFalse);
      expect(r.statusCode, 200);
      expect(r.message, 'ok');
      expect(r.dataAsMap, {'a': 1});
    });

    test('POST envía JSON y no manda token si authenticated=false', () async {
      late http.Request recibida;
      final api = ApiClient(
        baseUrl: 'http://api',
        tokenReader: () => 'jwt',
        client: MockClient((r) async {
          recibida = r;
          return _json({
            'datos': [1, 2],
          });
        }),
      );
      final r = await api.post('/x', body: {'k': 'v'}, authenticated: false);
      expect(recibida.headers['Content-Type'], 'application/json');
      expect(recibida.headers.containsKey('Authorization'), isFalse);
      expect(jsonDecode(recibida.body), {'k': 'v'});
      expect(r.dataAsMap, isEmpty);
      expect(r.message, 'No fue posible completar la operación.');
    });

    test('sin tokenReader no hay cabecera Authorization', () async {
      late http.BaseRequest recibida;
      final api = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((r) async {
          recibida = r;
          return _json({'datos': null});
        }),
      );
      await api.post('/x');
      expect(recibida.headers.containsKey('Authorization'), isFalse);
    });

    test('constructor sin cliente usa uno por omisión', () {
      expect(ApiClient(baseUrl: 'http://api').baseUrl, 'http://api');
    });

    test('401 autenticado avisa a onUnauthorized con el mensaje', () async {
      String? aviso;
      final api = ApiClient(
        baseUrl: 'http://api',
        tokenReader: () => 't',
        onUnauthorized: (m) => aviso = m,
        client: MockClient(
          (_) async => _json({'mensaje': 'Sesión reemplazada'}, 401),
        ),
      );
      await expectLater(
        api.get('/x'),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'mensaje',
            'Sesión reemplazada',
          ),
        ),
      );
      expect(aviso, 'Sesión reemplazada');
    });

    test('401 sin autenticación no dispara onUnauthorized', () async {
      var llamado = false;
      final api = ApiClient(
        baseUrl: 'http://api',
        onUnauthorized: (_) => llamado = true,
        client: MockClient((_) async => http.Response('', 401)),
      );
      await expectLater(
        api.post('/auth/login', authenticated: false),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'mensaje',
            contains('sesión terminó'),
          ),
        ),
      );
      expect(llamado, isFalse);
    });

    test('401 sin onUnauthorized definido solo lanza', () async {
      final api = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((_) async => http.Response('', 401)),
      );
      await expectLater(api.get('/x'), throwsA(isA<ApiException>()));
    });

    test('mensajes por omisión según el estado', () async {
      Future<String> mensaje(int estado) async {
        final api = ApiClient(
          baseUrl: 'http://api',
          client: MockClient((_) async => http.Response('no es json', estado)),
        );
        try {
          await api.get('/x');
        } on ApiException catch (e) {
          return e.message;
        }
        return '';
      }

      expect(await mensaje(403), contains('permiso'));
      expect(await mensaje(404), contains('No encontramos'));
      expect(await mensaje(429), contains('demasiadas'));
      expect(await mensaje(503), contains('no está disponible'));
      expect(await mensaje(418), contains('No fue posible'));
    });

    test('un 429 conserva la espera de Retry-After', () async {
      ApiClient clienteCon(String? retryAfter) => ApiClient(
        baseUrl: 'http://api',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({'mensaje': 'Demasiados intentos'}),
            429,
            headers: {
              'content-type': 'application/json; charset=utf-8',
              'retry-after': ?retryAfter,
            },
          ),
        ),
      );

      Future<ApiException> fallo(String? retryAfter) async {
        try {
          await clienteCon(retryAfter).post('/auth/login', body: {}, authenticated: false);
        } on ApiException catch (e) {
          return e;
        }
        throw StateError('debió fallar');
      }

      final conEspera = await fallo('42');
      expect(conEspera.esLimiteDeSolicitudes, isTrue);
      expect(conEspera.reintentarEn, const Duration(seconds: 42));
      expect((await fallo(null)).reintentarEn, isNull);
      expect((await fallo('basura')).reintentarEn, isNull);
    });

    test('esperaIndicada ignora valores inválidos y acota a una hora', () {
      expect(ApiClient.esperaIndicada(' 30 '), const Duration(seconds: 30));
      expect(ApiClient.esperaIndicada('999999'), const Duration(hours: 1));
      expect(ApiClient.esperaIndicada('0'), isNull);
      expect(ApiClient.esperaIndicada('-5'), isNull);
      expect(ApiClient.esperaIndicada('Wed, 21 Oct 2026 07:28:00 GMT'), isNull);
      expect(ApiClient.esperaIndicada(null), isNull);
    });

    test('respuesta 200 que no es un objeto JSON es inválida', () async {
      final api = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((_) async => http.Response('', 200)),
      );
      await expectLater(api.get('/x'), throwsA(isA<ConexionException>()));
      final api2 = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((_) async => http.Response('[1]', 200)),
      );
      await expectLater(api2.get('/x'), throwsA(isA<ConexionException>()));
    });

    test('tiempo agotado y fallo de red son ConexionException', () async {
      final lento = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((_) async {
          await Future<void>.delayed(const Duration(milliseconds: 200));
          return _json({});
        }),
      );
      await expectLater(
        lento.get('/x', timeout: const Duration(milliseconds: 20)),
        throwsA(
          isA<ConexionException>().having(
            (e) => e.message,
            'mensaje',
            contains('tardó'),
          ),
        ),
      );
      final caido = ApiClient(
        baseUrl: 'http://api',
        client: MockClient((_) async => throw http.ClientException('sin red')),
      );
      await expectLater(
        caido.get('/x'),
        throwsA(
          isA<ConexionException>().having(
            (e) => e.message,
            'mensaje',
            contains('comunicarnos'),
          ),
        ),
      );
    });
  });

  group('AppColors y Tono', () {
    test('cada tono tiene texto y fondo propios', () {
      expect(Tono.exito.texto, AppColors.exito);
      expect(Tono.exito.fondo, AppColors.exitoFondo);
      expect(Tono.pendiente.texto, AppColors.pendiente);
      expect(Tono.pendiente.fondo, AppColors.pendienteFondo);
      expect(Tono.rechazo.texto, AppColors.rechazo);
      expect(Tono.rechazo.fondo, AppColors.rechazoFondo);
      expect(Tono.aviso.texto, AppColors.aviso);
      expect(Tono.aviso.fondo, AppColors.avisoFondo);
      expect(Tono.neutro.texto, AppColors.tintaSuave);
      expect(Tono.neutro.fondo, AppColors.lineaSuave);
    });
  });

  group('Breakpoints', () {
    testWidgets('esAncho depende del ancho de la pantalla', (tester) async {
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      late bool ancho;
      late bool bastaBarra;
      Future<void> montar(double w) async {
        tester.view.physicalSize = Size(w, 800);
        await tester.pumpWidget(
          Builder(
            builder: (c) {
              ancho = Breakpoints.esAncho(c);
              bastaBarra = Breakpoints.esAncho(c, Breakpoints.barraLateral);
              return const SizedBox();
            },
          ),
        );
      }

      await montar(1000);
      expect(ancho, isTrue);
      await montar(900);
      expect(ancho, isFalse);
      expect(bastaBarra, isTrue);
    });
  });

  group('Monograma', () {
    Future<String> iniciales(WidgetTester t, String nombre) async {
      await t.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: Monograma(nombre: nombre),
        ),
      );
      return t.widget<Text>(find.byType(Text)).data!;
    }

    testWidgets('iniciales según el nombre', (tester) async {
      expect(await iniciales(tester, 'Telcel Recargas'), 'TR');
      expect(await iniciales(tester, 'Telcel'), 'TE');
      expect(await iniciales(tester, 'X'), 'X');
      expect(await iniciales(tester, '  '), '?');
      expect(await iniciales(tester, '--- ***'), '?');
    });

    testWidgets('el color es estable para el mismo nombre', (tester) async {
      Color color() =>
          (tester.widget<Container>(find.byType(Container)).decoration!
                  as BoxDecoration)
              .color!;
      await tester.pumpWidget(
        const Directionality(
          textDirection: TextDirection.ltr,
          child: Monograma(nombre: 'Telcel', tamano: 30),
        ),
      );
      final a = color();
      await tester.pumpWidget(
        const Directionality(
          textDirection: TextDirection.ltr,
          child: Monograma(nombre: 'telcel', tamano: 30),
        ),
      );
      expect(color(), a);
    });
  });
}
