import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../error/app_exception.dart';

/// Respuesta ya desenvuelta del contrato `{codigo, mensaje, datos}` de la API.
class ApiResponse {
  const ApiResponse({required this.statusCode, required this.message, this.data});
  final int statusCode;
  final String message;
  final Object? data;

  Map<String, dynamic> get dataAsMap =>
      data is Map<String, dynamic> ? data as Map<String, dynamic> : const {};
}

/// Cliente HTTP único de las dos aplicaciones. Es el adaptador técnico sobre
/// el que se apoyan todos los adaptadores de salida (repositorios).
class ApiClient {
  ApiClient({
    required this.baseUrl,
    this.tokenReader,
    this.onUnauthorized,
    http.Client? client,
  }) : _client = client ?? http.Client();

  final String baseUrl;
  final String? Function()? tokenReader;

  /// Se invoca cuando una ruta protegida responde 401 (token vencido, cerrado o
  /// reemplazado por otro inicio de sesión), con el mensaje del servidor.
  final void Function(String mensaje)? onUnauthorized;
  final http.Client _client;

  Future<ApiResponse> get(
    String path, {
    Map<String, String>? query,
    Duration timeout = const Duration(seconds: 20),
  }) => _send('GET', path, query: query, timeout: timeout);

  Future<ApiResponse> post(
    String path, {
    Object? body,
    Duration timeout = const Duration(seconds: 20),
    bool authenticated = true,
  }) => _send('POST', path, body: body, timeout: timeout, authenticated: authenticated);

  Future<ApiResponse> _send(
    String method,
    String path, {
    Map<String, String>? query,
    Object? body,
    required Duration timeout,
    bool authenticated = true,
  }) async {
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: query);
    final headers = <String, String>{
      'Accept': 'application/json',
      if (body != null) 'Content-Type': 'application/json',
    };
    final token = authenticated ? tokenReader?.call() : null;
    if (token != null) headers['Authorization'] = 'Bearer $token';

    final http.Response response;
    try {
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      response = await http.Response.fromStream(
        await _client.send(request).timeout(timeout),
      ).timeout(timeout);
    } on TimeoutException {
      throw const ConexionException(
        'El servidor tardó demasiado en responder.',
      );
    } on Object {
      throw const ConexionException();
    }

    final decoded = _decode(response.bodyBytes);
    final message = decoded is Map<String, dynamic> && decoded['mensaje'] is String
        ? decoded['mensaje'] as String
        : _defaultMessage(response.statusCode);

    if (response.statusCode >= 400) {
      if (response.statusCode == 401 && authenticated) onUnauthorized?.call(message);
      throw ApiException(response.statusCode, message);
    }
    if (decoded is! Map<String, dynamic>) {
      throw const ConexionException('La respuesta del servidor no es válida.');
    }
    return ApiResponse(
      statusCode: response.statusCode,
      message: message,
      data: decoded['datos'],
    );
  }

  Object? _decode(List<int> bytes) {
    if (bytes.isEmpty) return null;
    try {
      return jsonDecode(utf8.decode(bytes));
    } on FormatException {
      return null;
    }
  }

  String _defaultMessage(int status) => switch (status) {
    401 => 'Tu sesión terminó. Inicia sesión de nuevo.',
    403 => 'No tienes permiso para realizar esta operación.',
    404 => 'No encontramos lo que buscas.',
    429 => 'Hiciste demasiadas solicitudes. Espera un minuto.',
    >= 500 => 'El servicio no está disponible por ahora.',
    _ => 'No fue posible completar la operación.',
  };
}
