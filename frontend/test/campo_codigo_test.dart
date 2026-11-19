import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/ui/campo_codigo.dart';

import 'helpers/falsos.dart';

void main() {
  group('FormatoCodigo', () {
    final formato = FormatoCodigo(6);

    TextEditingValue editar(String anterior, String nuevo) => formato.formatEditUpdate(
          TextEditingValue(text: anterior),
          TextEditingValue(text: nuevo),
        );

    test('deja solo dígitos', () {
      expect(editar('', '12a3').text, '123');
    });

    test('pegar el código con espacios, guiones o texto alrededor', () {
      expect(editar('', '123 456').text, '123456');
      expect(editar('', '123-456').text, '123456');
      expect(editar('', 'Tu código es: 987654').text, '987654');
    });

    test('pegar el código completo reemplaza lo ya escrito', () {
      expect(editar('12', '12123456').text, '123456');
      expect(editar('123456', '123456654321').text, '654321');
    });

    test('teclear de más no pasa del límite', () {
      expect(editar('123456', '1234567').text, '123456');
    });

    test('el cursor queda al final', () {
      expect(editar('', '123').selection, const TextSelection.collapsed(offset: 3));
    });
  });

  testWidgets('Ctrl+V pega el código completo en los cuadros', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (llamada) async => llamada.method == 'Clipboard.getData'
          ? <String, dynamic>{'text': 'Tu código: 123 456'}
          : llamada.method == 'Clipboard.hasStrings'
              ? <String, dynamic>{'value': true}
              : null,
    );
    addTearDown(() => tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null));

    await montar(tester, CampoCodigo(controller: controller, autofocus: true));
    await tester.pump();

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.keyV);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    expect(controller.text, '123456');
    for (final digito in '123456'.split('')) {
      expect(find.text(digito), findsOneWidget);
    }
  });
}
