import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'api/quiz_api.dart';
import 'app.dart';
import 'auth/auth_controller.dart';
import 'auth/session_store.dart';

/// Where the API is. Override at build time, e.g.
/// `flutter build apk --dart-define=API_BASE_URL=https://quiz.example.org/api`.
/// The default reaches a local `docker compose` stack from the Android emulator.
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:3000/api');

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final client = ApiClient(baseUrl: apiBaseUrl, store: SecureSessionStore());
  final api = QuizApi(client);
  final auth = AuthController(client, api)..restore();
  runApp(QuizApp(api: api, auth: auth));
}
