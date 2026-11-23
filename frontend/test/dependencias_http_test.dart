import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/dependencias.dart';
import 'package:frontend/core/error/app_exception.dart';

import 'helpers/falsos.dart';

/// Sin `testWidgets`: aquí sí se permite abrir un servidor local real.
void main() {
  test('un 401 del servidor cierra la sesión con su mensaje', () async {
    final servidor = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    String? autorizacion;
    servidor.listen((peticion) {
      autorizacion = peticion.headers.value('authorization');
      peticion.response
        ..statusCode = 401
        ..headers.contentType = ContentType.json
        ..write(jsonEncode({'mensaje': 'Sesión reemplazada.'}));
      peticion.response.close();
    });

    final deps = Dependencias.crear(
      apiBaseUrl: 'http://127.0.0.1:${servidor.port}',
      almacenSesion: AlmacenFalso()..guardada = sesionPrueba(),
      autenticacion: GatewayFalso(),
    );
    await deps.sesion.restaurar();
    expect(deps.sesion.autenticado, isTrue);

    await expectLater(
      deps.pagos.consultarEstado('X'),
      throwsA(isA<ApiException>()),
    );
    expect(autorizacion, 'Bearer jwt');
    expect(deps.sesion.autenticado, isFalse);
    expect(deps.sesion.aviso, 'Sesión reemplazada.');

    deps.sesion.dispose();
    await servidor.close(force: true);
  });
}
