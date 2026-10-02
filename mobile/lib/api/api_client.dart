import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/session_store.dart';
import 'models.dart';

/// An error from the API; [message] is written for students and can be shown as it is.
class ApiException implements Exception {
  ApiException(this.status, this.code, this.message, [this.fieldErrors = const {}]);

  /// HTTP status, or 0 when the server could not be reached.
  final int status;
  final String code;
  final String message;
  final Map<String, String> fieldErrors;

  bool get isNetwork => status == 0;

  @override
  String toString() => message;
}

const _networkMessage = 'No internet connection. Please try again.';
const _genericMessage = 'Something went wrong. Please try again.';

/// Talks to the API, keeping the login session and renewing the access token when it expires.
class ApiClient {
  ApiClient({required this.baseUrl, required this.store, http.Client? httpClient})
      : _http = httpClient ?? http.Client();

  /// E.g. `https://quiz.example.org/api`.
  final String baseUrl;

  /// Where the login is kept between app launches.
  final SessionStore store;
  final http.Client _http;
  final _sessionChanges = StreamController<Session?>.broadcast();

  Session? _session;
  Future<Session?>? _refreshing;

  Session? get session => _session;

  /// Fires when the session changes, including when it ends because the login expired.
  Stream<Session?> get sessionChanges => _sessionChanges.stream;

  /// Loads the session saved on the device, if any.
  Future<Session?> restoreSession() async {
    _session = await store.read();
    return _session;
  }

  Future<void> setSession(Session? session) async {
    _session = session;
    await store.write(session);
    _sessionChanges.add(session);
  }

  /// Calls an endpoint that does not need a login.
  Future<dynamic> publicPost(String path, Object body) async {
    return _decode(await _send('POST', path, body: body));
  }

  Future<dynamic> get(String path) => _authorized('GET', path);

  Future<dynamic> post(String path, [Object? body]) => _authorized('POST', path, body: body);

  Future<dynamic> _authorized(String method, String path, {Object? body}) async {
    var res = await _send(method, path, body: body, token: _session?.accessToken);
    if (res.statusCode == 401 && _session != null) {
      final renewed = await _refresh();
      if (renewed == null) throw ApiException(401, 'SESSION_EXPIRED', 'Please log in again.');
      res = await _send(method, path, body: body, token: renewed.accessToken);
    }
    return _decode(res);
  }

  /// Gets a new token pair. Concurrent callers share one request, because each refresh token works once.
  Future<Session?> _refresh() {
    return _refreshing ??= () async {
      try {
        final current = _session;
        if (current == null) return null;
        final res = await _send('POST', '/auth/refresh', body: {'refreshToken': current.refreshToken});
        if (res.statusCode != 200) {
          await setSession(null);
          return null;
        }
        final next = Session.fromJson(jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
        await setSession(next);
        return next;
      } finally {
        _refreshing = null;
      }
    }();
  }

  Future<http.Response> _send(String method, String path, {Object? body, String? token}) async {
    final request = http.Request(method, Uri.parse('$baseUrl$path'));
    request.headers['Accept'] = 'application/json';
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    try {
      final streamed = await _http.send(request).timeout(const Duration(seconds: 20));
      return await http.Response.fromStream(streamed);
    } on http.ClientException {
      throw ApiException(0, 'NETWORK', _networkMessage);
    } on TimeoutException {
      throw ApiException(0, 'NETWORK', _networkMessage);
    }
  }

  dynamic _decode(http.Response res) {
    final text = utf8.decode(res.bodyBytes);
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return text.isEmpty ? null : jsonDecode(text);
    }
    Object? problem;
    try {
      problem = jsonDecode(text);
    } on FormatException {
      problem = null;
    }
    if (problem is! Map<String, dynamic>) throw ApiException(res.statusCode, 'ERROR', _genericMessage);
    final errors = problem['errors'];
    throw ApiException(
      res.statusCode,
      problem['code'] as String? ?? 'ERROR',
      problem['detail'] as String? ?? _genericMessage,
      errors is Map<String, dynamic> ? errors.map((key, value) => MapEntry(key, '$value')) : const {},
    );
  }
}
