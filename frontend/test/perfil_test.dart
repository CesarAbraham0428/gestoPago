import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/dependencias.dart';
import 'package:frontend/app/web/web_shell.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/format/formatters.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/ui/monograma.dart';
import 'package:frontend/perfil/application/perfil_controller.dart';
import 'package:frontend/perfil/domain/entities/perfil.dart';
import 'package:frontend/perfil/infraestructure/adapters/api_perfil_repository.dart';
import 'package:frontend/perfil/infraestructure/ui/perfil_page.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'helpers/falsos.dart';

Map<String, dynamic> _clienteJson({int id = 7}) => {
  'id': id,
  'primerNombre': 'Ana',
  'segundoNombre': null,
  'apellidoPaterno': 'Pérez',
  'apellidoMaterno': 'López',
  'fechaNacimiento': '1995-04-12',
  'curp': 'PELA950412MDFRPN09',
  'rfc': 'PELA950412AB1',
  'sexo': 'Femenino',
  'nacionalidad': 'Mexicana',
  'estadoCivil': 'Union libre',
  'correoElectronico': 'ana@example.com',
  'telefonoMovil': '5512345678',
  'telefonoAlternativo': null,
  'ocupacion': 'Ingeniera',
  'empresa': 'Acme',
  'ingresoMensual': 25000.0,
  'domicilio': {
    'calle': 'Reforma',
    'numeroExterior': '10',
    'numeroInterior': null,
    'colonia': 'Centro',
    'municipio': 'Cuauhtémoc',
    'estado': 'CDMX',
    'codigoPostal': '06000',
    'pais': 'México',
  },
  'cuentas': [
    {'id': 1, 'numeroCuenta': '123456789012345678', 'saldo': 900, 'estaActiva': true},
  ],
};

/// Cliente recién registrado que omitió empleo y domicilio.
Perfil _perfilMinimo() => Perfil(
  primerNombre: 'Ana',
  apellidoPaterno: 'Pérez',
  apellidoMaterno: 'López',
  fechaNacimiento: DateTime(1995, 4, 12),
  curp: 'PELA950412MDFRPN09',
  rfc: 'PELA950412AB1',
  sexo: 'Femenino',
  nacionalidad: 'Mexicana',
  correoElectronico: 'ana@example.com',
  telefonoMovil: '5512345678',
);

/// Recuadros sombreados de los datos sin registrar (no llevan texto; se reconocen por su etiqueta accesible).
final camposVacios = find.byWidgetPredicate(
  (w) => w is Semantics && w.properties.label == 'Sin registrar. Toca para completar',
);

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('dominio', () {
    test('cambiosEntre devuelve solo lo modificado, con domicilio anidado', () {
      final antes = perfilPrueba().aMapaEditable();
      final despues = Map<String, Object?>.from(antes)
        ..['empresa'] = 'Nueva'
        ..['domicilio'] = {
          ...(antes['domicilio']! as Map<String, Object?>),
          'codigoPostal': '01000',
        };
      expect(cambiosEntre(antes, despues), {
        'empresa': 'Nueva',
        'domicilio': {'codigoPostal': '01000'},
      });
      expect(cambiosEntre(antes, Map.of(antes)), isEmpty);
    });

    test('fechaIso y datos derivados', () {
      expect(fechaIso(DateTime(5, 3, 9)), '0005-03-09');
      final p = perfilPrueba();
      expect(p.nombreCompleto, 'Ana Pérez López');
      expect(p.domicilio!.lineaCalle, 'Reforma 10');
      expect(p.domicilio!.lineaZona, contains('06000'));
    });
  });

  group('adaptador HTTP', () {
    late List<http.Request> peticiones;
    late Map<String, Object?> Function(http.Request) responder;

    ApiClient api() => ApiClient(
      baseUrl: 'http://api',
      tokenReader: () => 'jwt',
      client: MockClient((r) async {
        peticiones.add(r);
        return http.Response(
          jsonEncode({'mensaje': 'ok', 'datos': responder(r)}),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    setUp(() => peticiones = []);

    test('obtener usa /usuarios/me y elige el cliente propio', () async {
      responder = (r) => r.url.path == '/usuarios/me'
          ? {'clienteId': 7, 'correo': 'ana@example.com'}
          : {
              'datos': [_clienteJson(id: 3), _clienteJson()],
            };
      final perfil = await ApiPerfilRepository(api()).obtener();
      expect(perfil.rfc, 'PELA950412AB1');
      expect(perfil.segundoNombre, isNull);
      expect(perfil.cuentas.single.saldo, 900);
      expect(jsonDecode(peticiones.last.body), {
        'correo': 'ana@example.com',
        'pagina': 0,
        'tamanio': 5,
      });
    });

    test('obtener falla si el cliente propio no viene en la respuesta', () async {
      responder = (r) => r.url.path == '/usuarios/me'
          ? {'clienteId': 99, 'correo': 'ana@example.com'}
          : {
              'datos': [_clienteJson()],
            };
      expect(ApiPerfilRepository(api()).obtener(), throwsA(isA<ConexionException>()));
    });

    test('actualizar envía PATCH con identificación y cambios', () async {
      responder = (_) => _clienteJson();
      await ApiPerfilRepository(api()).actualizar(
        rfc: 'PELA950412AB1',
        cambios: {'empresa': 'Nueva'},
      );
      expect(peticiones.single.method, 'PATCH');
      expect(peticiones.single.url.path, '/clientes');
      expect(jsonDecode(peticiones.single.body), {
        'identificacion': {'rfc': 'PELA950412AB1'},
        'cambios': {'empresa': 'Nueva'},
      });
    });

    test('perfilDesdeJson rechaza datos incompletos', () {
      expect(() => perfilDesdeJson({..._clienteJson(), 'rfc': null}), throwsA(isA<AppException>()));
      expect(() => perfilDesdeJson({..._clienteJson(), 'domicilio': 'x'}), throwsA(isA<AppException>()));
      expect(() => perfilDesdeJson({..._clienteJson(), 'ingresoMensual': 'x'}), throwsA(isA<AppException>()));
    });

    test('perfilDesdeJson acepta un cliente sin empleo ni domicilio', () {
      final p = perfilDesdeJson({
        ..._clienteJson(),
        'ocupacion': null,
        'empresa': null,
        'ingresoMensual': null,
        'domicilio': null,
      });
      expect(p.ocupacion, isNull);
      expect(p.empresa, isNull);
      expect(p.ingresoMensual, isNull);
      expect(p.domicilio, isNull);
      expect(p.aMapaEditable()['domicilio'], isNull);
    });
  });

  group('caso de uso', () {
    test('cargar, guardar solo cambios y errores', () async {
      final repo = PerfilFalso();
      final c = PerfilController(repo);
      expect(await c.guardar({}), isNull); // sin perfil no hace nada
      await c.cargar();
      expect(c.estado, EstadoPerfil.listo);

      expect(await c.guardar(c.perfil!.aMapaEditable()), isNull);
      expect(repo.cambiosRecibidos, isEmpty);

      final editado = c.perfil!.aMapaEditable()..['empresa'] = 'Nueva';
      expect(await c.guardar(editado), isNull);
      expect(repo.cambiosRecibidos.single, {'empresa': 'Nueva'});
      expect(c.perfil!.empresa, 'Nueva');

      repo.errorActualizar = const ApiException(400, 'Correo duplicado');
      expect(await c.guardar(c.perfil!.aMapaEditable()..['empresa'] = 'X'), 'Correo duplicado');
      repo.errorActualizar = StateError('x');
      expect(await c.guardar(c.perfil!.aMapaEditable()..['empresa'] = 'Y'), isNotNull);

      c.limpiar();
      expect(c.perfil, isNull);
      expect(c.estado, EstadoPerfil.inicial);
    });

    test('error al cargar', () async {
      final repo = PerfilFalso()..errorObtener = const ConexionException();
      final c = PerfilController(repo);
      await c.cargar();
      expect(c.estado, EstadoPerfil.error);
      repo.errorObtener = StateError('x');
      await c.cargar();
      expect(c.error, isNotNull);
    });
  });

  group('pantallas', () {
    testWidgets('perfil: ver, editar y guardar', (tester) async {
      final repo = PerfilFalso();
      final c = PerfilController(repo);
      await montar(tester, PerfilPage(controller: c), tamano: const Size(1000, 2400));
      await tester.pumpAndSettle();
      expect(find.text('Datos personales'), findsOneWidget);
      expect(find.text('Domicilio'), findsOneWidget);
      expect(find.text('Empleo'), findsOneWidget);
      expect(find.text('Acme'), findsOneWidget);
      // El banner saluda solo por el primer nombre.
      expect(find.text('Hola,'), findsOneWidget);
      expect(find.text('Ana'), findsNWidgets(2)); // banner y "Primer nombre"
      expect(find.text('Nombre completo'), findsNothing);
      expect(find.text('Apellido paterno'), findsOneWidget);
      expect(find.text('ana@example.com'), findsOneWidget); // solo en Contacto, no en el banner

      await tester.tap(find.text('Editar perfil'));
      await tester.pumpAndSettle();
      // Un campo editable sin datos se sombrea; con datos queda con el fondo normal.
      final empresa = find.widgetWithText(TextFormField, 'Empresa (opcional)');
      Color? relleno() => tester
          .widget<InputDecorator>(find.descendant(of: empresa, matching: find.byType(InputDecorator)))
          .decoration
          .fillColor;
      expect(relleno(), AppColors.superficie);
      await tester.enterText(empresa, '');
      await tester.pump();
      expect(relleno(), isNot(AppColors.superficie));
      await tester.enterText(empresa, 'Nueva');
      await tester.enterText(find.widgetWithText(TextFormField, 'Código postal'), '12');
      await tester.tap(find.text('Guardar cambios'));
      await tester.pumpAndSettle();
      expect(find.text('Debe tener 5 dígitos.'), findsOneWidget);
      expect(repo.cambiosRecibidos, isEmpty);

      await tester.enterText(find.widgetWithText(TextFormField, 'Código postal'), '06000');
      // Datos personales y correo se ven en su lugar pero no se pueden editar.
      for (final e in ['Primer nombre', 'Fecha de nacimiento', 'Sexo', 'Nacionalidad', 'CURP', 'RFC', 'Correo electrónico', 'Teléfono móvil']) {
        final campo = tester.widget<TextFormField>(find.widgetWithText(TextFormField, e));
        expect(campo.enabled, isFalse, reason: e);
      }
      expect(find.text('PELA950412AB1'), findsOneWidget);
      final alternativo = tester.widget<TextFormField>(find.widgetWithText(TextFormField, 'Teléfono alternativo (opcional)'));
      expect(alternativo.enabled, isNot(isFalse));
      await tester.tap(find.text('Guardar cambios'));
      // El aviso se quita solo a los 4 s: se comprueba mientras está visible.
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(repo.cambiosRecibidos.single, {'empresa': 'Nueva'});
      expect(find.text('Tus datos se guardaron.'), findsOneWidget);
      expect(find.text('Nueva'), findsOneWidget);
      await drenarToasts(tester);
      expect(find.text('Tus datos se guardaron.'), findsNothing);
    });

    testWidgets('perfil sin empleo ni domicilio: se ve vacío y se agregan al editar', (tester) async {
      final repo = PerfilFalso(_perfilMinimo());
      final c = PerfilController(repo);
      await montar(tester, PerfilPage(controller: c), tamano: const Size(1000, 3000));
      await tester.pumpAndSettle();
      expect(camposVacios, findsWidgets);
      expect(find.text('Estado civil'), findsOneWidget);

      await tester.tap(find.text('Editar perfil'));
      await tester.pumpAndSettle();
      // Sin capturar nada no se exige el domicilio ni hay nada que guardar.
      await tester.tap(find.text('Guardar cambios'));
      await tester.pumpAndSettle();
      expect(find.text('Este campo es obligatorio.'), findsNothing);
      expect(repo.cambiosRecibidos, isEmpty);

      // Sin cambios la pantalla vuelve a la vista: se entra a editar de nuevo.
      await drenarToasts(tester); // el aviso tapa el botón arriba a la derecha
      await tester.tap(find.text('Editar perfil'));
      await tester.pumpAndSettle();

      // Un domicilio a medias sí se rechaza: calle de 2 letras y el resto vacío.
      await tester.enterText(find.widgetWithText(TextFormField, 'Calle'), 'Re');
      await tester.tap(find.text('Guardar cambios'));
      await tester.pumpAndSettle();
      expect(find.text('Mínimo 3 caracteres.'), findsOneWidget);
      expect(find.text('Este campo es obligatorio.'), findsWidgets);
      expect(repo.cambiosRecibidos, isEmpty);

      Future<void> escribir(String etiqueta, String texto) =>
          tester.enterText(find.widgetWithText(TextFormField, etiqueta), texto);
      await escribir('Calle', 'Reforma');
      await escribir('Número exterior', '10');
      await escribir('Colonia', 'Centro');
      await escribir('Código postal', '06000');
      await escribir('Municipio o alcaldía', 'Cuauhtémoc');
      await escribir('Estado', 'CDMX');
      await escribir('Ocupación (opcional)', 'Contadora');
      await tester.tap(find.text('Guardar cambios'));
      await tester.pumpAndSettle();

      final cambios = repo.cambiosRecibidos.single;
      expect(cambios['ocupacion'], 'Contadora');
      expect(cambios.containsKey('empresa'), isFalse);
      expect((cambios['domicilio']! as Map)['calle'], 'Reforma');
      expect((cambios['domicilio']! as Map)['pais'], 'México');
      await drenarToasts(tester);
    });

    testWidgets('editar: errores en vivo y sin espacios ni símbolos', (tester) async {
      final c = PerfilController(PerfilFalso(perfilPrueba()));
      await montar(tester, PerfilPage(controller: c), tamano: const Size(1000, 2400));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Editar perfil'));
      await tester.pumpAndSettle();

      final calle = find.widgetWithText(TextFormField, 'Calle');
      // Error de longitud al escribir, sin pulsar "Guardar".
      await tester.enterText(calle, 'Re');
      await tester.pump();
      expect(find.text('Mínimo 3 caracteres.'), findsOneWidget);
      // Espacios y símbolos de SQL no se pueden escribir.
      await tester.enterText(calle, "Av Reforma'; DROP--");
      await tester.pump();
      expect(tester.widget<TextFormField>(calle).controller!.text, 'AvReformaDROP');
    });

    testWidgets('perfil: el teléfono alternativo se muestra con formato', (tester) async {
      final c = PerfilController(PerfilFalso(perfilPrueba(telefonoAlternativo: '5598765432')));
      await montar(tester, PerfilPage(controller: c), tamano: const Size(1000, 2400));
      await tester.pumpAndSettle();
      expect(find.text(formatoTelefono('5598765432')), findsOneWidget);
      expect(camposVacios, findsNothing);
    });

    testWidgets('perfil: error al guardar se muestra y cancelar vuelve', (tester) async {
      final repo = PerfilFalso()..errorActualizar = const ApiException(409, 'Correo en uso');
      final c = PerfilController(repo);
      await montar(tester, PerfilPage(controller: c), tamano: const Size(1000, 2400));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Editar perfil'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextFormField, 'Empresa (opcional)'), 'Nueva');
      await tester.tap(find.text('Guardar cambios'));
      await tester.pumpAndSettle();
      expect(find.text('Correo en uso'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(find.text('Editar perfil'), findsOneWidget);
    });

    testWidgets('perfil: error de carga con reintento', (tester) async {
      final repo = PerfilFalso()..errorObtener = const ConexionException('Sin red');
      await montar(tester, PerfilPage(controller: PerfilController(repo)));
      await tester.pumpAndSettle();
      expect(find.text('Sin red'), findsOneWidget);
      repo.errorObtener = null;
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('Datos personales'), findsOneWidget);
    });

    testWidgets('cuenta bancaria: saldo oculto por defecto y estado', (tester) async {
      await montar(tester, CuentasBancariasPage(controller: PerfilController(PerfilFalso())));
      await tester.pumpAndSettle();
      expect(find.text('Cuenta ${cuentaEnmascarada('123456789012345678')}'), findsOneWidget);
      expect(find.text('1234 5678 9012 3456 78'), findsWidgets);
      expect(find.text('Activa'), findsOneWidget);
      expect(find.text('Inactiva'), findsOneWidget);
      expect(find.text('Tus cuentas'), findsOneWidget);
      expect(find.byTooltip('Actualizar saldos'), findsOneWidget);
      expect(find.byType(Monograma), findsNothing); // sin el encabezado con nombre y correo
      expect(find.text(r'$900.00'), findsNothing); // el saldo arranca oculto
      await tester.tap(find.byTooltip('Mostrar saldo').first);
      await tester.pump();
      expect(find.text(r'$900.00'), findsOneWidget);
      await tester.tap(find.byTooltip('Ocultar saldo'));
      await tester.pump();
      expect(find.text(r'$900.00'), findsNothing);
    });

    testWidgets('cuentas bancarias: copiar número de cuenta', (tester) async {
      String? copiado;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          copiado = (call.arguments as Map)['text'] as String?;
        }
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await montar(tester, CuentasBancariasPage(controller: PerfilController(PerfilFalso())));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Copiar número de cuenta').first);
      await tester.pump();
      expect(copiado, '123456789012345678');
      expect(find.text('Número de cuenta copiado.'), findsOneWidget);
    });

    testWidgets('cuentas bancarias: sin cuentas', (tester) async {
      final repo = PerfilFalso(
        Perfil(
          primerNombre: 'Ana',
          apellidoPaterno: 'P',
          apellidoMaterno: 'L',
          fechaNacimiento: DateTime(1990),
          curp: 'c',
          rfc: 'r',
          sexo: 'Femenino',
          nacionalidad: 'M',
          estadoCivil: 'Soltero',
          correoElectronico: 'a@b.c',
          telefonoMovil: '5512345678',
          ocupacion: 'o',
          empresa: 'e',
          ingresoMensual: 1,
          domicilio: perfilPrueba().domicilio,
        ),
      );
      await montar(tester, CuentasBancariasPage(controller: PerfilController(repo)));
      await tester.pumpAndSettle();
      expect(find.text('Sin cuentas'), findsOneWidget);
    });
  });

  group('shell web', () {
    testWidgets('el botón de menú oculta y muestra la barra lateral', (tester) async {
      tester.view.physicalSize = const Size(1300, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final deps = Dependencias.crear(
        autenticacion: GatewayFalso(),
        almacenSesion: AlmacenFalso()..guardada = sesionPrueba(),
        catalogoRepositorio: CatalogoFalso(() async => []),
        pagosRepositorio: PagosFalso(),
        cuentasRepositorio: CuentasFalso(() async => []),
        perfilRepositorio: PerfilFalso(),
      );
      await deps.sesion.restaurar();
      await tester.pumpWidget(GestoPagoApp(dependencias: deps, shell: construirShellWeb));
      await tester.pumpAndSettle();

      double anchoMenu() => tester.getSize(find.byType(AnimatedContainer).first).width;
      expect(anchoMenu(), greaterThan(200));
      await tester.tap(find.byTooltip('Ocultar menú'));
      await tester.pumpAndSettle();
      expect(anchoMenu(), 0);
      await tester.tap(find.byTooltip('Mostrar menú'));
      await tester.pumpAndSettle();
      expect(anchoMenu(), greaterThan(200));

      // Navega a Mi perfil y el menú conserva su estado.
      await tester.tap(find.text('Mi perfil'));
      await tester.pumpAndSettle();
      expect(find.text('Datos personales'), findsOneWidget);
      GoRouter.of(tester.element(find.byType(Scaffold).first)).go('/cuentas');
      await tester.pumpAndSettle();
      expect(find.text('Cuenta ${cuentaEnmascarada('123456789012345678')}'), findsOneWidget);
      expect(find.text('1234 5678 9012 3456 78'), findsWidgets);

      // Al cerrar sesión se descartan los datos del perfil.
      await deps.sesion.cerrarSesion();
      await tester.pumpAndSettle();
      expect(deps.perfil.perfil, isNull);
      await tester.pumpWidget(const SizedBox());
      deps.sesion.dispose();
    });
  });
}
