import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

const apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

class AuthService {
  AuthService({http.Client? client, FlutterSecureStorage? secureStorage})
    : _client = client ?? http.Client(),
      _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const _offlineUserKey = 'offline_user_v1';
  static const _offlineSecretKey = 'offline_secret_v1';
  static const _offlineProductsKey = 'offline_products_v1';
  static const _offlinePeriod = Duration(days: 7);
  static const _requestTimeout = Duration(seconds: 8);

  final http.Client _client;
  final FlutterSecureStorage _secureStorage;

  Future<AppSession> login(String username, String password) async {
    final normalizedUsername = username.trim().toLowerCase();
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('$apiBaseUrl/auth/login'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'usuario': normalizedUsername,
              'password': password,
            }),
          )
          .timeout(_requestTimeout);
    } catch (error) {
      if (!_isConnectivityError(error)) rethrow;
      return _loginOffline(normalizedUsername, password);
    }
    if (response.statusCode != 200) {
      throw ApiException(
        _messageFrom(response.body, 'No fue posible iniciar sesión.'),
        response.statusCode,
      );
    }
    final auth = _parseAuthResponse(response.body);
    await _saveOfflineVerifier(
      normalizedUsername,
      password,
      auth.nombreCompleto,
    );
    return AppSession(
      username: auth.usuario,
      displayName: auth.nombreCompleto,
      token: auth.token,
      offline: false,
    );
  }

  Future<AppSession> register({
    required String username,
    required String password,
    required String name,
    required String paternalSurname,
    required String maternalSurname,
    required String email,
    required String phone,
  }) async {
    final normalizedUsername = username.trim().toLowerCase();
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('$apiBaseUrl/auth/register'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'usuario': normalizedUsername,
              'password': password,
              'nombre': name.trim(),
              'apellidoPaterno': paternalSurname.trim(),
              'apellidoMaterno': maternalSurname.trim(),
              'correo': email.trim(),
              'telefono': phone.trim(),
            }),
          )
          .timeout(_requestTimeout);
    } catch (error) {
      if (_isConnectivityError(error)) {
        throw const ApiException(
          'El registro requiere conexión con la API. Conéctate e inténtalo de nuevo.',
        );
      }
      rethrow;
    }
    if (response.statusCode != 201) {
      throw ApiException(
        _messageFrom(response.body, 'No fue posible crear la cuenta.'),
        response.statusCode,
      );
    }
    final auth = _parseAuthResponse(response.body);
    await _saveOfflineVerifier(
      normalizedUsername,
      password,
      auth.nombreCompleto,
    );
    return AppSession(
      username: auth.usuario,
      displayName: auth.nombreCompleto,
      token: auth.token,
      offline: false,
    );
  }

  Future<ProductsResult> loadProducts(AppSession session) async {
    if (session.token == null) {
      return ProductsResult(
        products: await _readCachedProducts(),
        offline: true,
        message: 'Sesión sin conexión. Se muestran los productos guardados en este equipo.',
      );
    }
    late final http.Response response;
    try {
      response = await _client
          .get(
            Uri.parse('$apiBaseUrl/productos'),
            headers: {'Authorization': 'Bearer ${session.token}'},
          )
          .timeout(_requestTimeout);
    } catch (error) {
      if (!_isConnectivityError(error)) rethrow;
      return ProductsResult(
        products: await _readCachedProducts(),
        offline: true,
        message: 'La API no está disponible. Se muestran los productos guardados en este equipo.',
      );
    }
    if (response.statusCode != 200) {
      if (response.statusCode >= 500) {
        return ProductsResult(
          products: await _readCachedProducts(),
          offline: true,
          message: _messageFrom(
            response.body,
            'La API no pudo cargar el catálogo. Se usa la copia local.',
          ),
        );
      }
      throw ApiException(
        _messageFrom(response.body, 'No fue posible cargar los productos.'),
        response.statusCode,
      );
    }
    final decoded = jsonDecode(response.body) as Map<String, dynamic>;
    final items = (decoded['productos'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(Product.fromJson)
        .toList(growable: false);
    await _writeCachedProducts(items);
    return ProductsResult(
      products: items,
      offline: false,
      message:
          decoded['mensaje'] as String? ?? 'Catálogo actualizado desde la API.',
    );
  }

  Future<AppSession> _loginOffline(String username, String password) async {
    final cachedJson = await _secureStorage.read(key: _offlineUserKey);
    final secretEncoded = await _secureStorage.read(key: _offlineSecretKey);
    if (cachedJson == null || secretEncoded == null) {
      throw const ApiException(
        'No hay conexión y este usuario no tiene una sesión guardada en este equipo.',
      );
    }
    final cachedUsers = jsonDecode(cachedJson) as Map<String, dynamic>;
    final cached = cachedUsers[username];
    if (cached is! Map<String, dynamic>) {
      throw const ApiException(
        'No hay conexión y este usuario no tiene una sesión guardada en este equipo.',
      );
    }
    final expiresAt = DateTime.fromMillisecondsSinceEpoch(
      cached['expiresAt'] as int,
    );
    if (cached['usuario'] != username ||
        cached['activo'] != true ||
        DateTime.now().isAfter(expiresAt)) {
      throw const ApiException(
        'La sesión sin conexión expiró o el usuario no coincide. Conéctate para iniciar sesión.',
      );
    }
    final candidate = _verifier(
      base64Decode(secretEncoded),
      username,
      password,
    );
    if (!_constantTimeEquals(candidate, cached['verificador'] as String)) {
      throw const ApiException('Usuario o contraseña incorrectos.');
    }
    return AppSession(
      username: username,
      displayName: cached['nombre'] as String? ?? username,
      token: null,
      offline: true,
    );
  }

  Future<void> _saveOfflineVerifier(
    String username,
    String password,
    String displayName,
  ) async {
    var secretEncoded = await _secureStorage.read(key: _offlineSecretKey);
    if (secretEncoded == null) {
      final random = Random.secure();
      secretEncoded = base64Encode(
        List<int>.generate(32, (_) => random.nextInt(256)),
      );
      await _secureStorage.write(key: _offlineSecretKey, value: secretEncoded);
    }
    final cachedJson = await _secureStorage.read(key: _offlineUserKey);
    final cachedUsers = cachedJson == null
        ? <String, dynamic>{}
        : Map<String, dynamic>.from(jsonDecode(cachedJson) as Map);
    cachedUsers[username] = {
      'usuario': username,
      'nombre': displayName,
      'activo': true,
      'verificador': _verifier(base64Decode(secretEncoded), username, password),
      'expiresAt': DateTime.now().add(_offlinePeriod).millisecondsSinceEpoch,
    };
    await _secureStorage.write(
      key: _offlineUserKey,
      value: jsonEncode(cachedUsers),
    );
  }

  String _verifier(List<int> secret, String username, String password) {
    return Hmac(
      sha256,
      secret,
    ).convert(utf8.encode('$username\u0000$password')).toString();
  }

  bool _constantTimeEquals(String left, String right) {
    if (left.length != right.length) return false;
    var difference = 0;
    for (var index = 0; index < left.length; index++) {
      difference |= left.codeUnitAt(index) ^ right.codeUnitAt(index);
    }
    return difference == 0;
  }

  bool _isConnectivityError(Object error) {
    if (error is TimeoutException || error is http.ClientException) {
      return true;
    }
    final message = error.toString().toLowerCase();
    return message.contains('socket') ||
        message.contains('handshake') ||
        message.contains('network') ||
        message.contains('connection refused') ||
        message.contains('connection reset') ||
        message.contains('failed host lookup') ||
        message.contains('xmlhttprequest') ||
        message.contains('failed to fetch');
  }

  String _messageFrom(String body, String fallback) {
    try {
      final decoded = jsonDecode(body) as Map<String, dynamic>;
      return decoded['mensaje'] as String? ?? fallback;
    } on Object {
      return fallback;
    }
  }

  Future<List<Product>> _readCachedProducts() async {
    final rawJson = await _secureStorage.read(key: _offlineProductsKey);
    if (rawJson == null || rawJson.isEmpty) return const [];
    try {
      final json = jsonDecode(rawJson) as List<dynamic>;
      return json
          .whereType<Map<String, dynamic>>()
          .map(Product.fromJson)
          .toList(growable: false);
    } on Object {
      return const [];
    }
  }

  Future<void> _writeCachedProducts(List<Product> products) async {
    await _secureStorage.write(
      key: _offlineProductsKey,
      value: jsonEncode(products.map((product) => product.toJson()).toList()),
    );
  }

  AuthPayload _parseAuthResponse(String body) {
    final decoded = jsonDecode(body) as Map<String, dynamic>;
    return AuthPayload(
      token: decoded['token'] as String,
      usuario: decoded['usuario'] as String,
      nombreCompleto:
          decoded['nombreCompleto'] as String? ?? decoded['usuario'] as String,
    );
  }
}

class ApiException implements Exception {
  const ApiException(this.message, [this.statusCode]);
  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class AppSession {
  const AppSession({
    required this.username,
    required this.displayName,
    required this.token,
    required this.offline,
  });

  final String username;
  final String displayName;
  final String? token;
  final bool offline;
}

class AuthPayload {
  const AuthPayload({
    required this.token,
    required this.usuario,
    required this.nombreCompleto,
  });
  final String token;
  final String usuario;
  final String nombreCompleto;
}

class ProductsResult {
  const ProductsResult({
    required this.products,
    required this.offline,
    required this.message,
  });
  final List<Product> products;
  final bool offline;
  final String message;
}

class Product {
  const Product({
    required this.name,
    required this.service,
    required this.serviceId,
    required this.productId,
    required this.price,
  });

  final String name;
  final String service;
  final int? serviceId;
  final int? productId;
  final double? price;

  factory Product.fromJson(Map<String, dynamic> json) => Product(
    name: json['producto'] as String? ?? 'Producto',
    service: json['servicio'] as String? ?? '',
    serviceId: json['idServicio'] as int?,
    productId: json['idProducto'] as int?,
    price: (json['precio'] as num?)?.toDouble(),
  );

  Map<String, dynamic> toJson() => {
    'producto': name,
    'servicio': service,
    'idServicio': serviceId,
    'idProducto': productId,
    'precio': price,
  };
}
