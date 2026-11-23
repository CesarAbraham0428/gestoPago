import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../error/app_exception.dart';
import '../error/mensajes_usuario.dart';

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

  Future<ApiResponse> put(String path, {Object? body}) =>
      _send('PUT', path, body: body, timeout: const Duration(seconds: 20));

  Future<ApiResponse> patch(String path, {Object? body}) =>
      _send('PATCH', path, body: body, timeout: const Duration(seconds: 20));

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
      throw const ConexionException(MensajesUsuario.tiempoAgotado);
    } on Object {
      throw const ConexionException();
    }

    final decoded = _decode(response.bodyBytes);
    final mensajeServidor = decoded is Map<String, dynamic> && decoded['mensaje'] is String
        ? decoded['mensaje'] as String
        : null;

    if (response.statusCode >= 400) {
      final message = MensajesUsuario.desdeServidor(response.statusCode, mensajeServidor);
      if (response.statusCode == 401 && authenticated) onUnauthorized?.call(message);
      throw ApiException(
        response.statusCode,
        message,
        reintentarEn: response.statusCode == 429
            ? esperaIndicada(response.headers['retry-after'])
            : null,
      );
    }
    if (decoded is! Map<String, dynamic>) {
      throw const ConexionException(MensajesUsuario.respuestaInvalida);
    }
    return ApiResponse(
      statusCode: response.statusCode,
      message: mensajeServidor ?? '',
      data: decoded['datos'],
    );
  }

  /// Segundos de la cabecera `Retry-After` como espera, acotada a una hora para
  /// que un valor absurdo no deje la pantalla bloqueada. Nula si no es válida.
  static Duration? esperaIndicada(String? cabecera) {
    final segundos = int.tryParse(cabecera?.trim() ?? '');
    if (segundos == null || segundos <= 0) return null;
    return Duration(seconds: segundos > 3600 ? 3600 : segundos);
  }

  Object? _decode(List<int> bytes) {
    if (bytes.isEmpty) return null;
    try {
      return jsonDecode(utf8.decode(bytes));
    } on FormatException {
      return null;
    }
  }
}
