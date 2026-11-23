import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/error/mensajes_usuario.dart';

void main() {
  group('MensajesUsuario.desdeServidor', () {
    test('oculta mensajes técnicos del servidor', () {
      for (final tecnico in [
        'Campo de pago no permitido: monto',
        'Solicitud inválida: revisa los campos, tipos y valores permitidos',
        'JSON inválido',
        'Ruta no encontrada',
        'Método HTTP no permitido para esta ruta',
        'Usuario inactivo o inexistente',
        'La operación incumple una restricción del registro',
        'GestoPago devolvió una respuesta vacía',
      ]) {
        final r = MensajesUsuario.desdeServidor(400, tecnico);
        expect(r, MensajesUsuario.datosInvalidos, reason: tecnico);
      }
      expect(
        MensajesUsuario.desdeServidor(401, 'Usuario inactivo o inexistente'),
        MensajesUsuario.sesionTerminada,
      );
    });

    test('un 5xx nunca muestra el texto del servidor, salvo los de pago', () {
      expect(
        MensajesUsuario.desdeServidor(500, 'Ocurrió un error interno en la API'),
        MensajesUsuario.servicioNoDisponible,
      );
      expect(
        MensajesUsuario.desdeServidor(503, 'Lo que sea'),
        MensajesUsuario.servicioNoDisponible,
      );
      expect(
        MensajesUsuario.desdeServidor(502, 'El pago no se realizó; no se realizó ningún cargo'),
        'El pago no se realizó. No se realizó ningún cargo.',
      );
    });

    test('conserva mensajes de negocio claros y los puntúa', () {
      expect(
        MensajesUsuario.desdeServidor(422, 'Saldo insuficiente para realizar el pago'),
        'Saldo insuficiente para realizar el pago.',
      );
      expect(MensajesUsuario.desdeServidor(404, null), MensajesUsuario.noEncontrado);
      expect(MensajesUsuario.desdeServidor(404, '  '), MensajesUsuario.noEncontrado);
    });
  });
}
