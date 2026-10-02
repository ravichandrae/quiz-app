import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../api/quiz_api.dart';

/// Who is logged in. Widgets listen to it to switch between the login and home screens.
class AuthController extends ChangeNotifier {
  AuthController(this._client, this._api) {
    // The client ends the session itself when the login has expired.
    _subscription = _client.sessionChanges.listen((_) => notifyListeners());
  }

  final ApiClient _client;
  final QuizApi _api;
  late final StreamSubscription<Session?> _subscription;
  bool _restored = false;

  /// False until the saved login has been checked at startup.
  bool get restored => _restored;

  SessionUser? get user => _client.session?.user;

  Future<void> restore() async {
    await _client.restoreSession();
    _restored = true;
    notifyListeners();
  }

  Future<void> login(String mobile, String pin) async {
    await _client.setSession(await _api.login(mobile, pin));
  }

  Future<void> register({
    required String name,
    required String mobile,
    required String pin,
    String? school,
    String? email,
  }) async {
    await _client.setSession(
      await _api.register(name: name, mobile: mobile, pin: pin, school: school, email: email),
    );
  }

  Future<void> logout() async {
    final session = _client.session;
    await _client.setSession(null);
    if (session != null) {
      // Best effort: the phone has already forgotten the login even if this fails.
      try {
        await _api.logout(session.refreshToken);
      } on ApiException {
        // Ignored.
      }
    }
  }

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}
