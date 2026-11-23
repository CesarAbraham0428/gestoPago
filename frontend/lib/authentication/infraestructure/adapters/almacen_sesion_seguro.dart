import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../domain/entities/sesion.dart';
import '../../domain/ports/almacen_sesion.dart';

/// Adaptador del puerto [AlmacenSesion] sobre almacenamiento cifrado del
/// sistema (Keychain / Keystore; en web, almacenamiento del navegador).
class AlmacenSesionSeguro implements AlmacenSesion {
  AlmacenSesionSeguro([FlutterSecureStorage? storage])
    : _storage = storage ?? const FlutterSecureStorage();

  static const _clave = 'gestopago.sesion.v1';
  final FlutterSecureStorage _storage;

  @override
  Future<Sesion?> leer() async {
    final crudo = await _storage.read(key: _clave);
    if (crudo == null) return null;
    try {
      final json = jsonDecode(crudo) as Map<String, dynamic>;
      return Sesion(
        token: json['token'] as String,
        correo: json['correo'] as String,
        nombreCompleto: json['nombreCompleto'] as String,
        expiraEn: DateTime.fromMillisecondsSinceEpoch(json['expiraEn'] as int),
      );
    } on Object {
      await borrar();
      return null;
    }
  }

  @override
  Future<void> guardar(Sesion sesion) => _storage.write(
    key: _clave,
    value: jsonEncode({
      'token': sesion.token,
      'correo': sesion.correo,
      'nombreCompleto': sesion.nombreCompleto,
      'expiraEn': sesion.expiraEn.millisecondsSinceEpoch,
    }),
  );

  @override
  Future<void> borrar() => _storage.delete(key: _clave);
}
