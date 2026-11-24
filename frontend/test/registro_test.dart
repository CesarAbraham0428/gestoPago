import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/authentication/application/sesion_controller.dart';
import 'package:frontend/authentication/domain/ports/registro_gateway.dart';
import 'package:frontend/authentication/domain/validadores_registro.dart';
import 'package:frontend/authentication/infraestructure/adapters/api_registro_gateway.dart';
import 'package:frontend/authentication/infraestructure/ui/login_page.dart';
import 'package:frontend/authentication/infraestructure/ui/registro_page.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/error/mensajes_usuario.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

class _RegistroFalso implements RegistroGateway {
  Object? error;
  final List<Map<String, Object?>> enviados = [];

  /// Nombres de la API que el servidor dirá que ya existen.
  List<String> repetidosEnServidor = [];
  final List<Map<String, String>> consultas = [];
  Object? errorAlComprobar;

  @override
  Future<List<String>> repetidos(Map<String, String> datos) async {
    // ignore: only_throw_errors
    if (errorAlComprobar != null) throw errorAlComprobar!;
    consultas.add(datos);
    return repetidosEnServidor.where(datos.containsKey).toList();
  }

  @override
  Future<void> registrar(Map<String, Object?> datos) async {
    // ignore: only_throw_errors
    if (error != null) throw error!;
    enviados.add(datos);
  }
}

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('ValidadoresRegistro', () {
    test('nombre de pila: mínimo 3 letras; apellidos: mínimo 2', () {
      expect(ValidadoresRegistro.nombre('Al', min: 3), isNotNull);
      expect(ValidadoresRegistro.nombre('Ana', min: 3), isNull);
      expect(ValidadoresRegistro.nombre('Jo', opcional: true, min: 3), isNotNull);
      expect(ValidadoresRegistro.nombre('', opcional: true, min: 3), isNull);
      expect(ValidadoresRegistro.nombre('Li'), isNull);
    });

    test('requerido con mínimo y máximo (calle y colonia: 3 a 100)', () {
      expect(ValidadoresRegistro.requerido('Ab', min: 3, max: 100), isNotNull);
      expect(ValidadoresRegistro.requerido('Abc', min: 3, max: 100), isNull);
      expect(ValidadoresRegistro.requerido('x' * 100, min: 3, max: 100), isNull);
      expect(ValidadoresRegistro.requerido('x' * 101, min: 3, max: 100), isNotNull);
    });

    test('ingreso opcional acepta vacío pero no un monto inválido', () {
      expect(ValidadoresRegistro.ingreso('', opcional: true), isNull);
      expect(ValidadoresRegistro.ingreso('0', opcional: true), isNotNull);
    });

    test('nombre: letras, 2 a 50, opcional vacío', () {
      expect(ValidadoresRegistro.nombre('María José'), isNull);
      expect(ValidadoresRegistro.nombre(''), isNotNull);
      expect(ValidadoresRegistro.nombre('', opcional: true), isNull);
      expect(ValidadoresRegistro.nombre('A'), isNotNull);
      expect(ValidadoresRegistro.nombre('Ana2'), isNotNull);
    });

    test('CURP y RFC con el formato de la API (acepta minúsculas)', () {
      expect(ValidadoresRegistro.curp('HEGG560427MVZRRL04'), isNull);
      expect(ValidadoresRegistro.curp('hegg560427mvzrrl04'), isNull);
      expect(ValidadoresRegistro.curp('HEGG560427'), isNotNull);
      expect(ValidadoresRegistro.rfc('VECJ880326XXX'), isNull);
      expect(ValidadoresRegistro.rfc('VECJ88'), isNotNull);
      expect(ValidadoresRegistro.curp(''), isNotNull);
      expect(ValidadoresRegistro.rfc(''), isNotNull);
    });

    test('teléfono, código postal e ingreso', () {
      expect(ValidadoresRegistro.telefono('5512345678'), isNull);
      expect(ValidadoresRegistro.telefono('551234'), isNotNull);
      expect(ValidadoresRegistro.telefono('', opcional: true), isNull);
      expect(ValidadoresRegistro.telefono(''), isNotNull);
      expect(ValidadoresRegistro.codigoPostal('06600'), isNull);
      expect(ValidadoresRegistro.codigoPostal('066'), isNotNull);
      expect(ValidadoresRegistro.ingreso('15000.50'), isNull);
      expect(ValidadoresRegistro.ingreso('0'), isNotNull);
      expect(ValidadoresRegistro.ingreso('abc'), isNotNull);
      expect(ValidadoresRegistro.requerido('  '), isNotNull);
      expect(ValidadoresRegistro.requerido('x' * 11, max: 10), isNotNull);
    });

    test('longitudes máximas como en el servidor', () {
      expect(ValidadoresRegistro.nombre('a' * 50), isNull);
      expect(ValidadoresRegistro.nombre('a' * 51), isNotNull);
      expect(ValidadoresRegistro.requerido('x' * 250), isNull);
      expect(ValidadoresRegistro.requerido('x' * 251), isNotNull);
      expect(ValidadoresRegistro.requerido('x' * 1000, max: 1000), isNull);
      expect(ValidadoresRegistro.requerido('x' * 1001, max: 1000), isNotNull);
    });

    test('correo con la regla de la base de datos', () {
      expect(ValidadoresRegistro.correo('ana@example.com'), isNull);
      expect(ValidadoresRegistro.correo('ana.perez@mail.example.com'), isNull);
      for (final malo in [
        'ana@example',
        'a@b..com',
        'a@.com',
        'a b@x.com',
        '@x.com',
        'a@x.com.',
      ]) {
        expect(ValidadoresRegistro.correo(malo), isNotNull, reason: malo);
      }
      expect(ValidadoresRegistro.correo('${'a' * 90}@example.com'), isNotNull);
      expect(ValidadoresRegistro.correo(''), isNotNull);
    });

    test('mayoría de edad: exactamente 18 años cumplidos', () {
      final hoy = DateTime(2026, 11, 20);
      expect(
        ValidadoresRegistro.fechaMaximaNacimiento(hoy),
        DateTime(2008, 11, 20),
      );
      expect(
        ValidadoresRegistro.fechaNacimiento(DateTime(2008, 11, 20), hoy: hoy),
        isNull,
      );
      expect(
        ValidadoresRegistro.fechaNacimiento(DateTime(2008, 11, 21), hoy: hoy),
        isNotNull,
      );
      expect(
        ValidadoresRegistro.fechaNacimiento(DateTime(1980, 1, 1), hoy: hoy),
        isNull,
      );
      expect(ValidadoresRegistro.fechaNacimiento(null, hoy: hoy), isNotNull);
      // 29 de febrero: hace 18 años cae en el 28 si ese año no fue bisiesto.
      expect(
        ValidadoresRegistro.fechaMaximaNacimiento(DateTime(2028, 2, 29)),
        DateTime(2010, 2, 28),
      );
      expect(
        ValidadoresRegistro.fechaMaximaNacimiento(DateTime(2026, 2, 28)),
        DateTime(2008, 2, 28),
      );
    });

    test('contraseña: pide los cinco requisitos y la confirmación', () {
      expect(ValidadoresRegistro.password('Segura123!'), isNull);
      expect(ValidadoresRegistro.password('Segura12345!'), isNull); // 12
      expect(ValidadoresRegistro.password('Segura123456!'), isNotNull); // 13
      for (final mala in [
        'segura123!',
        'SEGURA123!',
        'Segurísima!',
        'Segura1234',
        'Ab1!',
        'Aa1!${'x' * 80}',
      ]) {
        expect(ValidadoresRegistro.password(mala), isNotNull, reason: mala);
      }
      expect(ValidadoresRegistro.password(''), isNotNull);
      expect(
        ValidadoresRegistro.confirmacion('Segura123!', 'Segura123!'),
        isNull,
      );
      expect(ValidadoresRegistro.confirmacion('otra', 'Segura123!'), isNotNull);
      expect(ValidadoresRegistro.confirmacion('', 'Segura123!'), isNotNull);
    });
  });

  group('ApiRegistroGateway', () {
    ApiClient api(
      int estado, {
      void Function(http.Request)? alVer,
      Object? datos,
      String mensaje =
          'Verifique la información capturada e intente nuevamente.',
    }) =>
        ApiClient(
          baseUrl: 'http://api',
          tokenReader: () => 'jwt',
          client: MockClient((r) async {
            alVer?.call(r);
            return http.Response(
              jsonEncode({
                'mensaje': mensaje,
                'datos': datos,
              }),
              estado,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          }),
        );

    test('envía POST /clientes sin token', () async {
      http.Request? vista;
      await ApiRegistroGateway(api(201, alVer: (r) => vista = r))
          .registrar({'primerNombre': 'Ana'});
      expect(vista!.method, 'POST');
      expect(vista!.url.path, '/clientes');
      expect(vista!.headers.containsKey('Authorization'), isFalse);
      expect(jsonDecode(vista!.body), {'primerNombre': 'Ana'});
    });

    test('409 conserva el mensaje del servidor que nombra los datos', () async {
      await expectLater(
        ApiRegistroGateway(
          api(409, mensaje: 'Ya están registrados: CURP y RFC'),
        ).registrar({}),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'mensaje',
            'Ya están registrados: CURP y RFC.',
          ),
        ),
      );
    });

    test('repetidos consulta /clientes/disponibilidad sin token', () async {
      http.Request? vista;
      final r = await ApiRegistroGateway(
        api(
          200,
          alVer: (q) => vista = q,
          datos: {
            'duplicados': ['rfc', 'telefonoMovil'],
          },
        ),
      ).repetidos({'rfc': 'VECJ880326XXX', 'telefonoMovil': '5512345678'});
      expect(r, ['rfc', 'telefonoMovil']);
      expect(vista!.method, 'POST');
      expect(vista!.url.path, '/clientes/disponibilidad');
      expect(vista!.headers.containsKey('Authorization'), isFalse);
      expect(jsonDecode(vista!.body), {
        'rfc': 'VECJ880326XXX',
        'telefonoMovil': '5512345678',
      });
    });

    test('repetidos sin la lista devuelve vacío', () async {
      expect(await ApiRegistroGateway(api(200)).repetidos({'curp': 'X'}), isEmpty);
    });

    test('otros errores se propagan', () async {
      await expectLater(
        ApiRegistroGateway(api(400)).registrar({}),
        throwsA(isA<ApiException>().having((e) => e.statusCode, 'estado', 400)),
      );
    });
  });

  group('RegistroPage', () {
    late _RegistroFalso registro;
    late int volvio;

    setUp(() {
      registro = _RegistroFalso();
      volvio = 0;
    });

    Future<void> abrir(
      WidgetTester tester, {
      Size tamano = const Size(900, 1800),
    }) async {
      tester.view.physicalSize = tamano;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: RegistroPage(registro: registro, alVolver: () => volvio++),
        ),
      );
    }

    Finder campo(String nombre) => find.byKey(ValueKey('reg-$nombre'));
    Future<void> escribir(WidgetTester t, String nombre, String texto) =>
        t.enterText(campo(nombre), texto);
    Future<void> boton(WidgetTester t, String texto) async {
      await t.tap(find.widgetWithText(FilledButton, texto));
      await t.pumpAndSettle();
    }

    Future<void> elegir(
      WidgetTester t,
      String desplegable,
      String opcion,
    ) async {
      await t.tap(campo(desplegable));
      await t.pumpAndSettle();
      await t.tap(find.text(opcion).last);
      await t.pumpAndSettle();
    }

    Future<void> pasoNombre(WidgetTester t) async {
      await escribir(t, 'primerNombre', 'Ana');
      await escribir(t, 'apellidoPaterno', 'Pérez');
      await escribir(t, 'apellidoMaterno', 'López');
      await t.tap(campo('fecha'));
      await t.pumpAndSettle();
      await t.tap(find.text('OK'));
      await t.pumpAndSettle();
      await elegir(t, 'Sexo', 'Femenino');
      await boton(t, 'Continuar');
    }

    Future<void> pasoIdentidad(WidgetTester t) async {
      await elegir(t, 'Estado civil', 'Union libre');
      await escribir(t, 'curp', 'hegg560427mvzrrl04');
      await escribir(t, 'rfc', 'VECJ880326XXX');
      await escribir(t, 'telefonoMovil', '5512345678');
      await boton(t, 'Continuar');
    }

    Future<void> paso1(WidgetTester t) async {
      await pasoNombre(t);
      await pasoIdentidad(t);
    }

    Future<void> paso2(WidgetTester t) async {
      await escribir(t, 'empresa', 'Acme SA');
      await escribir(t, 'ocupacion', 'Contadora');
      await escribir(t, 'ingresoMensual', '15000.50');
      await boton(t, 'Continuar');
    }

    Future<void> paso3(WidgetTester t) async {
      await escribir(t, 'calle', 'Reforma');
      await escribir(t, 'numeroExterior', '10');
      await escribir(t, 'colonia', 'Centro');
      await escribir(t, 'municipio', 'Cuauhtémoc');
      await escribir(t, 'estado', 'CDMX');
      await escribir(t, 'codigoPostal', '06600');
      await boton(t, 'Continuar');
    }

    Future<void> paso4(WidgetTester t) async {
      await escribir(t, 'correo', 'ana@example.com');
      await escribir(t, 'password', 'Segura123!');
      await escribir(t, 'confirmacion', 'Segura123!');
    }

    testWidgets('no avanza con campos vacíos y muestra los errores', (
      tester,
    ) async {
      await abrir(tester);
      await boton(tester, 'Continuar');
      expect(find.text('Paso 1 de 5'), findsOneWidget);
      expect(find.text('Este campo es obligatorio.'), findsWidgets);
      expect(
        find.text('Selecciona tu fecha de nacimiento (18 años o más).'),
        findsOneWidget,
      );
    });

    testWidgets('recorre los cinco pasos y registra con los datos correctos', (
      tester,
    ) async {
      await abrir(tester);
      expect(find.text('Tu nombre'), findsOneWidget);
      await paso1(tester);
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      await paso2(tester);
      expect(find.text('Paso 4 de 5'), findsOneWidget);
      await paso3(tester);
      expect(find.text('Acceso a tu cuenta'), findsOneWidget);
      expect(find.textContaining(r'saldo de $0.00'), findsOneWidget);
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');

      expect(find.text('¡Tu cuenta está lista!'), findsOneWidget);
      final d = registro.enviados.single;
      expect(d['primerNombre'], 'Ana');
      expect(d['segundoNombre'], isNull);
      expect(d['curp'], 'HEGG560427MVZRRL04');
      expect(d['sexo'], 'Femenino');
      expect(d['estadoCivil'], 'Union libre');
      expect(d['telefonoAlternativo'], isNull);
      expect(d['ingresoMensual'], 15000.5);
      expect(d['correoElectronico'], 'ana@example.com');
      expect(d['password'], 'Segura123!');
      expect(
        RegExp(r'^\d{4}-\d{2}-\d{2}$')
            .hasMatch(d['fechaNacimiento']! as String),
        isTrue,
      );
      final dom = d['domicilio']! as Map;
      expect(dom['numeroInterior'], isNull);
      expect(dom['pais'], 'México');
      expect(dom['codigoPostal'], '06600');

      await tester.tap(find.text('Ir a iniciar sesión'));
      expect(volvio, 1);
    });

    testWidgets('empresa y domicilio se pueden omitir: viajan como null', (tester) async {
      await abrir(tester);
      await paso1(tester);
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      expect(find.text('Paso 4 de 5'), findsOneWidget);
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      expect(find.text('Acceso a tu cuenta'), findsOneWidget);
      expect(find.text('Omitir'), findsNothing); // el acceso sí es obligatorio
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');

      final d = registro.enviados.single;
      expect(d['ocupacion'], isNull);
      expect(d['empresa'], isNull);
      expect(d['ingresoMensual'], isNull);
      expect(d['domicilio'], isNull);
      expect(d['primerNombre'], 'Ana');
    });

    testWidgets('Omitir descarta lo escrito en ese paso', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await escribir(tester, 'empresa', 'Acme SA');
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');
      expect(registro.enviados.single['empresa'], isNull);
    });

    testWidgets('Continuar en un paso opcional sigue validando todo el paso', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await escribir(tester, 'empresa', 'Acme SA');
      await boton(tester, 'Continuar');
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      expect(find.text('Este campo es obligatorio.'), findsWidgets);
    });

    testWidgets('los errores se muestran al escribir, sin pulsar Continuar', (tester) async {
      await abrir(tester);
      await escribir(tester, 'primerNombre', 'Al');
      await tester.pump();
      expect(find.text('Solo letras, de 3 a 50 caracteres.'), findsOneWidget);
      await escribir(tester, 'primerNombre', 'Alan');
      await tester.pump();
      expect(find.text('Solo letras, de 3 a 50 caracteres.'), findsNothing);

      await boton(tester, 'Continuar'); // no avanza: faltan datos del paso 1
      await pasoNombre(tester);
      await escribir(tester, 'curp', 'HEGG56');
      await escribir(tester, 'rfc', 'VECJ88');
      await escribir(tester, 'telefonoMovil', '551');
      await tester.pump();
      expect(find.text('Revisa la CURP: son 18 caracteres.'), findsOneWidget);
      expect(find.text('Revisa el RFC: son 12 o 13 caracteres.'), findsOneWidget);
      expect(find.text('Debe tener 10 dígitos.'), findsOneWidget);
    });

    testWidgets('la confirmación avisa al instante y se revalida si cambia la contraseña', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await escribir(tester, 'password', 'Segura123!');
      await escribir(tester, 'confirmacion', 'Segura');
      await tester.pump();
      expect(find.text('Las contraseñas no coinciden.'), findsOneWidget);
      await escribir(tester, 'confirmacion', 'Segura123!');
      await tester.pump();
      expect(find.text('Las contraseñas no coinciden.'), findsNothing);
      // Cambiar la contraseña después deja la confirmación desactualizada: se avisa.
      await escribir(tester, 'password', 'Otra1234!');
      await tester.pump();
      expect(find.text('Las contraseñas no coinciden.'), findsOneWidget);
    });

    testWidgets('el estado civil es opcional', (tester) async {
      await abrir(tester);
      await pasoNombre(tester);
      expect(find.text('Estado civil (opcional)'), findsOneWidget);
      await escribir(tester, 'curp', 'hegg560427mvzrrl04');
      await escribir(tester, 'rfc', 'VECJ880326XXX');
      await escribir(tester, 'telefonoMovil', '5512345678');
      await boton(tester, 'Continuar'); // sin elegir estado civil
      expect(find.text('Paso 3 de 5'), findsOneWidget);
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');
      expect(registro.enviados.single['estadoCivil'], isNull);
    });

    testWidgets('calle y colonia piden al menos 3 caracteres', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await paso2(tester);
      await escribir(tester, 'calle', 'Re');
      await escribir(tester, 'colonia', 'Ce');
      await boton(tester, 'Continuar');
      expect(find.text('Paso 4 de 5'), findsOneWidget);
      expect(find.text('Mínimo 3 caracteres.'), findsNWidgets(2));
    });

    testWidgets('el primer nombre pide al menos 3 letras', (tester) async {
      await abrir(tester);
      await escribir(tester, 'primerNombre', 'Al');
      await boton(tester, 'Continuar');
      expect(find.text('Solo letras, de 3 a 50 caracteres.'), findsOneWidget);
    });

    testWidgets('la contraseña se limita a 12 caracteres y la confirmación tiene su ojito', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Omitir'));
      await tester.pumpAndSettle();
      await escribir(tester, 'password', 'Segura12345!XYZ');
      await tester.pump();
      expect(
        tester.widget<TextFormField>(campo('password')).controller!.text,
        'Segura12345!',
      );
      TextField conf() => tester.widget<TextField>(
        find.descendant(of: campo('confirmacion'), matching: find.byType(TextField)),
      );
      expect(conf().obscureText, isTrue);
      await tester.tap(find.byTooltip('Mostrar confirmación'));
      await tester.pump();
      expect(conf().obscureText, isFalse);
      expect(find.byTooltip('Ocultar confirmación'), findsOneWidget);
    });

    testWidgets('Atrás conserva lo escrito', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await tester.tap(find.text('Atrás'));
      await tester.pumpAndSettle();
      expect(find.text('Paso 4 de 5'), findsNothing);
      expect(find.text('Paso 2 de 5'), findsOneWidget);
    });

    testWidgets('el ojito muestra la contraseña y "hecho" en la confirmación envía', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await paso2(tester);
      await paso3(tester);
      await paso4(tester);

      await tester.tap(find.byTooltip('Mostrar contraseña'));
      await tester.pump();
      expect(find.byTooltip('Ocultar contraseña'), findsOneWidget);

      await tester.showKeyboard(campo('confirmacion'));
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(registro.enviados, hasLength(1));
    });

    testWidgets('las contraseñas distintas bloquean el envío', (tester) async {
      await abrir(tester);
      await paso1(tester);
      await paso2(tester);
      await paso3(tester);
      await escribir(tester, 'correo', 'ana@example.com');
      await escribir(tester, 'password', 'Segura123!');
      await escribir(tester, 'confirmacion', 'Distinta123!');
      await boton(tester, 'Crear mi cuenta');
      expect(find.text('Las contraseñas no coinciden.'), findsOneWidget);
      expect(registro.enviados, isEmpty);
    });

    testWidgets(
      'si el servidor rechaza, muestra el mensaje y permite reintentar',
      (tester) async {
        registro.error = const ApiException(
          409,
          'Ya están registrados: CURP y RFC.',
        );
        await abrir(tester, tamano: const Size(400, 2200));
        await paso1(tester);
        await paso2(tester);
        await paso3(tester);
        await paso4(tester);
        await boton(tester, 'Crear mi cuenta');
        expect(find.text('Ya están registrados: CURP y RFC.'), findsOneWidget);
        expect(find.text('Paso 5 de 5'), findsOneWidget);

        registro.error = null;
        await boton(tester, 'Crear mi cuenta');
        expect(find.text('¡Tu cuenta está lista!'), findsOneWidget);
      },
    );

    testWidgets(
      'avisa en el paso de datos qué dato está repetido y deja corregirlo',
      (tester) async {
        registro.repetidosEnServidor = ['rfc', 'telefonoMovil'];
        await abrir(tester);
        await pasoNombre(tester);
        await pasoIdentidad(tester);

        expect(find.text('Paso 2 de 5'), findsOneWidget);
        expect(find.text('Este RFC ya está registrado.'), findsOneWidget);
        expect(find.text('Este teléfono ya está registrado.'), findsOneWidget);
        expect(find.text('Esta CURP ya está registrada.'), findsNothing);
        expect(registro.consultas.single, {
          'curp': 'HEGG560427MVZRRL04',
          'rfc': 'VECJ880326XXX',
          'telefonoMovil': '5512345678',
        });

        // Al corregir un dato desaparece su aviso; el otro permanece.
        await escribir(tester, 'telefonoMovil', '5599999999');
        await tester.pumpAndSettle();
        expect(find.text('Este teléfono ya está registrado.'), findsNothing);
        expect(find.text('Este RFC ya está registrado.'), findsOneWidget);

        // Mientras el RFC siga repetido no se avanza (sin volver a consultar).
        await boton(tester, 'Continuar');
        expect(find.text('Paso 2 de 5'), findsOneWidget);
        expect(registro.consultas, hasLength(1));

        await escribir(tester, 'rfc', 'VECJ880326YYY');
        registro.repetidosEnServidor = [];
        await boton(tester, 'Continuar');
        expect(find.text('Paso 3 de 5'), findsOneWidget);
      },
    );

    testWidgets('avisa en el paso de acceso que el correo está repetido', (
      tester,
    ) async {
      registro.repetidosEnServidor = ['correoElectronico'];
      await abrir(tester);
      await paso1(tester);
      await paso2(tester);
      await paso3(tester);
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');

      expect(
        find.text('Este correo ya está registrado. Inicia sesión o usa otro.'),
        findsOneWidget,
      );
      expect(find.text('Paso 5 de 5'), findsOneWidget);
      expect(registro.consultas.last, {'correoElectronico': 'ana@example.com'});
      expect(registro.enviados, isEmpty);
    });

    testWidgets('si falla la comprobación se queda en el paso con un aviso', (
      tester,
    ) async {
      registro.errorAlComprobar = const ConexionException();
      await abrir(tester);
      await pasoNombre(tester);
      await elegir(tester, 'Estado civil', 'Union libre');
      await escribir(tester, 'curp', 'hegg560427mvzrrl04');
      await escribir(tester, 'rfc', 'VECJ880326XXX');
      await escribir(tester, 'telefonoMovil', '5512345678');
      await boton(tester, 'Continuar');
      expect(find.text('Paso 2 de 5'), findsOneWidget);
      expect(
        find.text(MensajesUsuario.sinConexion),
        findsOneWidget,
      );
    });

    testWidgets('un error inesperado muestra un mensaje genérico', (
      tester,
    ) async {
      registro.error = StateError('boom');
      await abrir(tester);
      await paso1(tester);
      await paso2(tester);
      await paso3(tester);
      await paso4(tester);
      await boton(tester, 'Crear mi cuenta');
      expect(
        find.text('No fue posible crear tu cuenta. Inténtalo de nuevo.'),
        findsOneWidget,
      );
    });

    testWidgets('cada paso cabe en el viewport sin hacer scroll', (
      tester,
    ) async {
      await abrir(tester, tamano: const Size(591, 650));
      double extra() => tester
          .state<ScrollableState>(find.byType(Scrollable).first)
          .position
          .maxScrollExtent;

      expect(extra(), 0, reason: 'nombre');
      await pasoNombre(tester);
      expect(extra(), 0, reason: 'identidad');
      await pasoIdentidad(tester);
      expect(extra(), 0, reason: 'empresa');
      await paso2(tester);
      expect(extra(), 0, reason: 'domicilio');
      await paso3(tester);
      expect(extra(), 0, reason: 'acceso');
    });

    testWidgets('el enlace inferior regresa al login', (tester) async {
      await abrir(tester);
      await tester.tap(find.text('¿Ya tienes cuenta? Inicia sesión'));
      expect(volvio, 1);
    });
  });

  group('Login → crear cuenta', () {
    testWidgets('la sección aparece solo si hay destino y lo invoca', (
      tester,
    ) async {
      final sesion = SesionController(
        gateway: GatewayFalso(),
        almacen: AlmacenFalso(),
      );
      addTearDown(sesion.dispose);
      tester.view.physicalSize = const Size(500, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: LoginPage(sesion: sesion),
        ),
      );
      expect(find.text('Crear cuenta'), findsNothing);

      var abierto = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: LoginPage(sesion: sesion, alCrearCuenta: () => abierto++),
        ),
      );
      await tester.tap(find.text('Crear cuenta'));
      expect(abierto, 1);
    });
  });
}
