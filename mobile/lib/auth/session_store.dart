import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/models.dart';

/// Where the login is kept between app launches.
abstract interface class SessionStore {
  Future<Session?> read();

  /// Saves the session, or forgets it when [session] is null.
  Future<void> write(Session? session);
}

/// Keeps the login in Android's encrypted storage.
class SecureSessionStore implements SessionStore {
  static const _key = 'session';
  final _storage = const FlutterSecureStorage();

  @override
  Future<Session?> read() async {
    try {
      final raw = await _storage.read(key: _key);
      return raw == null ? null : Session.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      // Unreadable (e.g. after the device's keys changed): start logged out.
      await _storage.delete(key: _key);
      return null;
    }
  }

  @override
  Future<void> write(Session? session) async {
    if (session == null) {
      await _storage.delete(key: _key);
    } else {
      await _storage.write(key: _key, value: jsonEncode(session.toJson()));
    }
  }
}

/// Keeps the login only while the app runs; used in tests.
class MemorySessionStore implements SessionStore {
  MemorySessionStore([this._session]);

  Session? _session;

  @override
  Future<Session?> read() async => _session;

  @override
  Future<void> write(Session? session) async => _session = session;
}
