import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/catalogos/api_catalogos_repository.dart';
import 'package:frontend/core/error/app_exception.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/ui/selector_catalogo.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('ApiCatalogosRepository', () {
    ApiCatalogosRepository repo(int Function(http.Request) respuesta, List<String> pedidos) =>
        ApiCatalogosRepository(
          ApiClient(
            baseUrl: 'http://api',
            client: MockClient((r) async {
              pedidos.add('${r.method} ${r.url.path}');
              final estado = respuesta(r);
              return http.Response(
                jsonEncode(
                  estado == 200
                      ? {
                          'codigo': 0,
                          'mensaje': 'ok',
                          'datos': {
                            'paises': ['Argentina', 'México'],
                            'nacionalidades': ['Argentina', 'Mexicana'],
                            'ocupaciones': ['Estudiante'],
                          },
                        }
                      : {'mensaje': 'falló'},
                ),
                estado,
                headers: {'content-type': 'application/json; charset=utf-8'},
              );
            }),
          ),
        );

    test('lee las tres listas y las pide una sola vez', () async {
      final pedidos = <String>[];
      final r = repo((_) => 200, pedidos);

      final c = await r.obtener();
      await r.obtener();

      expect(c.paises, ['Argentina', 'México']);
      expect(c.nacionalidades, ['Argentina', 'Mexicana']);
      expect(c.ocupaciones, ['Estudiante']);
      expect(pedidos, ['GET /catalogos']);
    });

    test('si falla no guarda el error: la siguiente consulta reintenta', () async {
      final pedidos = <String>[];
      var estado = 503;
      final r = repo((_) => estado, pedidos);

      await expectLater(r.obtener(), throwsA(isA<AppException>()));
      estado = 200;
      final c = await r.obtener();

      expect(c.paises, isNotEmpty);
      expect(pedidos.length, 2);
    });
  });

  group('SelectorCatalogo', () {
    Future<TextEditingController> montar(
      WidgetTester tester,
      String inicial, {
      bool opcional = false,
    }) async {
      final c = TextEditingController(text: inicial);
      addTearDown(c.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SelectorCatalogo(
              key: const ValueKey('sel'),
              controlador: c,
              etiqueta: 'País',
              opciones: const ['Argentina', 'México'],
              opcional: opcional,
            ),
          ),
        ),
      );
      return c;
    }

    testWidgets('elegir una opción escribe en el controlador', (tester) async {
      final c = await montar(tester, 'México');

      await tester.tap(find.byKey(const ValueKey('sel')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Argentina').last);
      await tester.pumpAndSettle();

      expect(c.text, 'Argentina');
    });

    testWidgets('un valor anterior al catálogo se muestra y se conserva', (tester) async {
      final c = await montar(tester, 'Mexico');

      expect(find.text('Mexico'), findsOneWidget);
      expect(c.text, 'Mexico');
    });

    testWidgets('opcional ofrece "Sin especificar" y vacía el campo', (tester) async {
      final c = await montar(tester, 'México', opcional: true);

      await tester.tap(find.byKey(const ValueKey('sel')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sin especificar').last);
      await tester.pumpAndSettle();

      expect(c.text, isEmpty);
    });

    testWidgets('si es obligatorio y está vacío, pide elegir', (tester) async {
      final c = TextEditingController();
      addTearDown(c.dispose);
      final form = GlobalKey<FormState>();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Form(
              key: form,
              child: SelectorCatalogo(
                controlador: c,
                etiqueta: 'País',
                opciones: const ['México'],
              ),
            ),
          ),
        ),
      );

      expect(form.currentState!.validate(), isFalse);
      await tester.pump();

      expect(find.text('Selecciona una opción.'), findsOneWidget);
    });
  });
}
