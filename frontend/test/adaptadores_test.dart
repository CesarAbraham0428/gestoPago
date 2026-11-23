import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/domain/entities/desafio_verificacion.dart';
import 'package:frontend/authentication/domain/entities/sesion.dart';
import 'package:frontend/authentication/infraestructure/adapters/almacen_sesion_seguro.dart';
import 'package:frontend/authentication/infraestructure/adapters/api_autenticacion_gateway.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/error/mensajes_usuario.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/pagos/domain/entities/solicitud_compra.dart';
import 'package:frontend/pagos/infraestructure/adapters/api_cuentas_repository.dart';
import 'package:frontend/pagos/infraestructure/adapters/api_pagos_repository.dart';
import 'package:frontend/productos/domain/entities/producto.dart';
import 'package:frontend/productos/infraestructure/adapters/api_catalogo_repository.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _desafio = DesafioVerificacion(
  desafioId: 'd1',
  correo: 'ana@example.com',
  vigencia: Duration(minutes: 5),
);

/// Cliente HTTP cuyo cuerpo `datos` y estado se fijan por prueba.
class _Servidor {
  _Servidor(this.datos, [this.estado = 200]);
  Object? datos;
  int estado;
  http.Request? ultima;

  ApiClient get api => ApiClient(
    baseUrl: 'http://api',
    tokenReader: () => 'jwt',
    client: MockClient((r) async {
      ultima = r;
      return http.Response(
        jsonEncode({'mensaje': 'msg', 'datos': datos}),
        estado,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    }),
  );

  Map<String, dynamic> get cuerpo =>
      jsonDecode(ultima!.body) as Map<String, dynamic>;
}

SolicitudCompra _solicitud({String? telefono, String? referencia}) =>
    SolicitudCompra(
      producto: const Producto(
        nombre: 'Recarga',
        servicio: 'Telcel',
        idProducto: 3,
        idServicio: 4,
        tipoFront: 1,
        precio: 50,
      ),
      numeroCuenta: '123456789012345678',
      monto: 50,
      pin: '1234',
      telefono: telefono,
      referencia: referencia,
    );

void main() {
  group('ApiAutenticacionGateway', () {
    test('iniciarSesion devuelve el desafío', () async {
      final s = _Servidor({'desafioId': 'x', 'expiraEnMs': 60000});
      final d = await ApiAutenticacionGateway(
        s.api,
      ).iniciarSesion(correo: 'ana@e.com', password: 'pw');
      expect(d.desafioId, 'x');
      expect(d.correo, 'ana@e.com');
      expect(d.vigencia, const Duration(minutes: 1));
      expect(d.enrolamiento, isNull);
      expect(s.ultima!.headers.containsKey('Authorization'), isFalse);
    });

    test('iniciarSesion con la app sin enlazar trae el secreto y la URI del QR', () async {
      final s = _Servidor({
        'desafioId': 'x',
        'expiraEnMs': 1000,
        'enrolamiento': {'secreto': 'ABCD', 'otpauthUri': 'otpauth://totp/GestoPago:a'},
      });
      final d = await ApiAutenticacionGateway(
        s.api,
      ).iniciarSesion(correo: 'ana@e.com', password: 'pw');
      expect(d.enrolamiento!.secreto, 'ABCD');
      expect(d.enrolamiento!.otpauthUri, 'otpauth://totp/GestoPago:a');
    });

    test('un enrolamiento incompleto es una respuesta inválida', () async {
      for (final enrolamiento in <Object?>[
        'texto',
        {'secreto': 'ABCD'},
        {'secreto': '', 'otpauthUri': 'otpauth://x'},
      ]) {
        final s = _Servidor({'desafioId': 'x', 'expiraEnMs': 1000, 'enrolamiento': enrolamiento});
        expect(
          ApiAutenticacionGateway(s.api).iniciarSesion(correo: 'a@e.com', password: 'pw'),
          throwsA(isA<ConexionException>()),
        );
      }
    });

    test('iniciarSesion con respuesta inválida', () async {
      for (final datos in <Object?>[
        null,
        {'desafioId': '', 'expiraEnMs': 1},
        {'desafioId': 'x'},
      ]) {
        final gw = ApiAutenticacionGateway(_Servidor(datos).api);
        await expectLater(
          gw.iniciarSesion(correo: 'a@e.com', password: 'p'),
          throwsA(isA<ConexionException>()),
        );
      }
    });

    test('verificarCodigo abre la sesión', () async {
      final s = _Servidor({
        'token': 'jwt',
        'expiraEnMs': 3600000,
        'usuario': 'ana@e.com',
        'nombreCompleto': 'Ana Pérez',
      });
      final sesion = await ApiAutenticacionGateway(s.api).verificarCodigo(
        desafio: _desafio,
        codigo: '123456',
        cerrarSesionAnterior: true,
      );
      expect(sesion.token, 'jwt');
      expect(sesion.nombreCompleto, 'Ana Pérez');
      expect(s.cuerpo['cerrarSesionAnterior'], isTrue);
      expect(sesion.estaVigente, isTrue);
    });

    test('verificarCodigo usa valores por omisión del desafío', () async {
      final s = _Servidor({'token': 'jwt', 'expiraEnMs': 1000});
      final sesion = await ApiAutenticacionGateway(s.api).verificarCodigo(
        desafio: _desafio,
        codigo: '123456',
        cerrarSesionAnterior: false,
      );
      expect(sesion.correo, 'ana@example.com');
      expect(sesion.nombreCompleto, '');
    });

    test('verificarCodigo: 409, otros errores y respuesta inválida', () async {
      Future<Sesion> verificar(_Servidor s) =>
          ApiAutenticacionGateway(s.api).verificarCodigo(
            desafio: _desafio,
            codigo: '123456',
            cerrarSesionAnterior: false,
          );

      await expectLater(
        verificar(_Servidor(null, 409)),
        throwsA(isA<SesionActivaException>()),
      );
      await expectLater(
        verificar(_Servidor(null, 401)),
        throwsA(isA<ApiException>()),
      );
      await expectLater(
        verificar(_Servidor(null, 403)),
        throwsA(
          isA<ApiException>()
              .having((e) => e.statusCode, 'statusCode', 403)
              .having((e) => e.message, 'message', MensajesUsuario.cuentaNoDisponible),
        ),
      );
      for (final datos in <Object?>[
        null,
        {'token': '', 'expiraEnMs': 1},
        {'token': 't'},
      ]) {
        await expectLater(
          verificar(_Servidor(datos)),
          throwsA(isA<ConexionException>()),
        );
      }
    });

    test('cerrarSesion llama a /auth/logout con token', () async {
      final s = _Servidor(null);
      await ApiAutenticacionGateway(s.api).cerrarSesion();
      expect(s.ultima!.url.path, '/auth/logout');
      expect(s.ultima!.headers['Authorization'], 'Bearer jwt');
    });
  });

  group('AlmacenSesionSeguro', () {
    setUp(() => FlutterSecureStorage.setMockInitialValues({}));

    test('guardar, leer y borrar', () async {
      final almacen = AlmacenSesionSeguro();
      expect(await almacen.leer(), isNull);
      final sesion = Sesion(
        token: 'jwt',
        correo: 'ana@e.com',
        nombreCompleto: 'Ana',
        expiraEn: DateTime.fromMillisecondsSinceEpoch(4102444800000),
      );
      await almacen.guardar(sesion);
      final leida = await almacen.leer();
      expect(leida?.token, 'jwt');
      expect(leida?.nombreCompleto, 'Ana');
      expect(leida?.expiraEn, sesion.expiraEn);
      await almacen.borrar();
      expect(await almacen.leer(), isNull);
    });

    test('un contenido corrupto se descarta', () async {
      const storage = FlutterSecureStorage();
      await storage.write(key: 'gestopago.sesion.v1', value: 'no es json');
      final almacen = AlmacenSesionSeguro(storage);
      expect(await almacen.leer(), isNull);
      expect(await storage.read(key: 'gestopago.sesion.v1'), isNull);
    });
  });

  group('ApiCuentasRepository', () {
    test('solo devuelve cuentas válidas y activas', () async {
      final s = _Servidor({
        'datos': [
          {'id': 1, 'numeroCuenta': 'A', 'saldo': 10, 'estaActiva': true},
          {'id': 2, 'numeroCuenta': 'B', 'saldo': 5.5, 'estaActiva': false},
          {'id': 'x', 'numeroCuenta': 'C', 'saldo': 1, 'estaActiva': true},
          {'id': 4, 'numeroCuenta': 7, 'saldo': 1, 'estaActiva': true},
          {'id': 5, 'numeroCuenta': 'E', 'saldo': 'mucho', 'estaActiva': true},
          'basura',
        ],
      });
      final cuentas = await ApiCuentasRepository(s.api).obtenerActivas();
      expect(cuentas.map((c) => c.id), [1]);
      expect(cuentas.single.saldo, 10);
      expect(s.ultima!.url.path, '/cuentas/consultar');
    });

    test('sin lista devuelve vacío', () async {
      final s = _Servidor({'datos': 'nada'});
      expect(await ApiCuentasRepository(s.api).obtenerActivas(), isEmpty);
    });

    test('lee el bloqueo del PIN y ignora fechas ilegibles', () async {
      final s = _Servidor({
        'datos': [
          {
            'id': 1,
            'numeroCuenta': 'A',
            'saldo': 10,
            'estaActiva': true,
            'pinBloqueadoHasta': '2026-03-05T12:15:00Z',
          },
          {'id': 2, 'numeroCuenta': 'B', 'saldo': 5, 'estaActiva': true, 'pinBloqueadoHasta': 'ayer'},
          {'id': 3, 'numeroCuenta': 'C', 'saldo': 5, 'estaActiva': true},
        ],
      });
      final cuentas = await ApiCuentasRepository(s.api).obtenerActivas();
      expect(cuentas[0].pinBloqueadoHasta, DateTime.utc(2026, 3, 5, 12, 15));
      expect(cuentas[1].pinBloqueadoHasta, isNull);
      expect(cuentas[2].pinBloqueadoHasta, isNull);
    });
  });

  group('ApiCatalogoRepository', () {
    test('convierte productos y descarta los inválidos', () async {
      final s = _Servidor({
        'productos': [
          {
            'idProducto': 1,
            'idServicio': 2,
            'tipoFront': 1,
            'producto': ' Recarga 50 ',
            'servicio': 'Telcel',
            'precio': 50,
            'idCatTipoServicio': 10,
            'tipoReferencia': ' Teléfono ',
            'legend': ' Ley ',
            'hasDigitoVerificador': true,
          },
          {'idProducto': 2, 'idServicio': 3, 'tipoFront': 2},
          {'idProducto': 'x', 'idServicio': 3, 'tipoFront': 2},
          {'idProducto': 2, 'idServicio': 'x', 'tipoFront': 2},
          {'idProducto': 2, 'idServicio': 3, 'tipoFront': 'x'},
          5,
        ],
      });
      final lista = await ApiCatalogoRepository(s.api).obtenerCatalogo();
      expect(lista.length, 2);
      final a = lista.first;
      expect(a.nombre, 'Recarga 50');
      expect(a.precio, 50);
      expect(a.idCatTipoServicio, 10);
      expect(a.tipoReferencia, 'Teléfono');
      expect(a.leyenda, 'Ley');
      expect(a.tieneDigitoVerificador, isTrue);
      final b = lista.last;
      expect(b.nombre, 'Producto');
      expect(b.servicio, 'Servicio');
      expect(b.precio, isNull);
      expect(b.tieneDigitoVerificador, isFalse);
    });

    test('sin lista devuelve vacío', () async {
      final s = _Servidor({'productos': 'x'});
      expect(await ApiCatalogoRepository(s.api).obtenerCatalogo(), isEmpty);
    });
  });

  group('ApiPagosRepository', () {
    test('pagar envía lo necesario y arma el comprobante', () async {
      final s = _Servidor({
        'estado': 'APROBADO',
        'monto': 50,
        'comision': 2.5,
        'idTx': 99,
        'numAutorizacion': 'AUT',
        'referencia': 'REF',
        'saldoDisponible': 120.5,
        'fecha': '2026-01-01',
        'pin': 'CODIGO',
        'legend': 'Gracias',
      });
      final c = await ApiPagosRepository(
        s.api,
      ).pagar(_solicitud(telefono: '5512345678', referencia: 'R1'));
      expect(c.estaAprobado, isTrue);
      expect(c.comision, 2.5);
      expect(c.idTx, 99);
      expect(c.numAutorizacion, 'AUT');
      expect(c.saldoDisponible, 120.5);
      expect(c.codigoCanje, 'CODIGO');
      expect(c.leyenda, 'Gracias');
      expect(s.cuerpo['telefono'], '5512345678');
      expect(s.cuerpo['referencia'], 'R1');
      expect(s.cuerpo['pinAutorizacion'], '1234');
    });

    test('pagar sin teléfono/referencia ni datos opcionales', () async {
      final s = _Servidor({'estado': 'PENDIENTE'});
      final c = await ApiPagosRepository(s.api).pagar(_solicitud());
      expect(c.monto, 50);
      expect(c.comision, 0);
      expect(s.cuerpo.containsKey('telefono'), isFalse);
      expect(s.cuerpo.containsKey('referencia'), isFalse);
    });

    test('pagar con respuesta sin estado es inválida', () async {
      final s = _Servidor({'monto': 1});
      await expectLater(
        ApiPagosRepository(s.api).pagar(_solicitud()),
        throwsA(isA<ConexionException>()),
      );
    });

    test('consultarEstado', () async {
      final s = _Servidor({
        'upc': 'U1',
        'estado': 'RECHAZADO',
        'mensaje': 'No',
        'monto': 10,
        'comision': 1,
        'referencia': 'R',
        'idTx': 5,
        'numAutorizacion': 'A',
      });
      final e = await ApiPagosRepository(s.api).consultarEstado('U0');
      expect(e.upc, 'U1');
      expect(e.rechazado, isTrue);
      expect(e.monto, 10);
      expect(e.idTx, 5);
    });

    test('consultarEstado con valores por omisión y respuesta inválida', () async {
      final s = _Servidor({'estado': 'PENDIENTE'});
      final e = await ApiPagosRepository(s.api).consultarEstado('U0');
      expect(e.upc, 'U0');
      expect(e.mensaje, '');
      expect(e.monto, isNull);
      await expectLater(
        ApiPagosRepository(_Servidor({}).api).consultarEstado('U0'),
        throwsA(isA<ConexionException>()),
      );
    });

    test('historial: páginas, descartes y valores por omisión', () async {
      final s = _Servidor({
        'datos': [
          {
            'upc': 'U1',
            'fecha': '2026-02-03T10:00:00',
            'monto': 10,
            'comision': 1,
            'total': 11,
            'producto': 'P',
            'servicio': 'S',
            'referencia': 'R',
            'telefono': 'T',
            'idTx': 3,
            'numAutorizacion': 'A',
            'estado': 'RECHAZADO',
          },
          {'upc': 'U2', 'fecha': '2026-02-04T10:00:00', 'monto': 5},
          {'upc': 'U3', 'fecha': 'no-fecha', 'monto': 5},
          {'upc': 3, 'fecha': '2026-02-04T10:00:00', 'monto': 5},
          {'upc': 'U4', 'fecha': '2026-02-04T10:00:00'},
          7,
        ],
        'pagina': 1,
        'totalPaginas': 4,
        'totalElementos': 40,
      });
      final p = await ApiPagosRepository(s.api).historial(pagina: 1, tamanio: 20);
      expect(p.elementos.map((c) => c.upc), ['U1', 'U2']);
      expect(p.elementos.last.total, 5);
      expect(p.elementos.last.comision, 0);
      expect(p.elementos.first.estado, 'RECHAZADO');
      expect(p.elementos.last.estado, 'APROBADO'); // sin dato se asume aprobado
      expect(p.pagina, 1);
      expect(p.totalPaginas, 4);
      expect(p.totalElementos, 40);
      expect(s.ultima!.url.queryParameters, {'pagina': '1', 'tamanio': '20'});
    });

    test('historial sin metadatos usa la página pedida', () async {
      final s = _Servidor({'datos': 'x'});
      final p = await ApiPagosRepository(s.api).historial(pagina: 2, tamanio: 5);
      expect(p.elementos, isEmpty);
      expect(p.pagina, 2);
      expect(p.totalPaginas, 1);
      expect(p.totalElementos, 0);
    });
  });
}
