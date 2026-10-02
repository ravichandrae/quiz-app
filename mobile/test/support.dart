import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:schoolmela_quiz/api/api_client.dart';
import 'package:schoolmela_quiz/api/models.dart';
import 'package:schoolmela_quiz/api/quiz_api.dart';
import 'package:schoolmela_quiz/app.dart';
import 'package:schoolmela_quiz/auth/auth_controller.dart';
import 'package:schoolmela_quiz/auth/session_store.dart';

typedef Handler = Future<http.Response> Function(http.Request request);

/// A fake API: each request is answered by the first route whose key ("METHOD /path") it starts with.
class FakeBackend {
  FakeBackend(this.routes);

  final Map<String, Handler> routes;
  final List<http.Request> requests = [];

  late final client = MockClient((request) async {
    requests.add(request);
    final key = '${request.method} ${request.url.path.replaceFirst('/api', '')}';
    for (final entry in routes.entries) {
      if (key.startsWith(entry.key)) return entry.value(request);
    }
    throw StateError('Unexpected request: $key');
  });

  /// JSON bodies of requests whose path ends with [suffix].
  List<Object?> bodiesTo(String suffix) => requests
      .where((r) => r.url.path.endsWith(suffix) && r.body.isNotEmpty)
      .map((r) => jsonDecode(r.body))
      .toList();
}

Future<http.Response> json(Object? body, [int status = 200]) async =>
    http.Response.bytes(utf8.encode(jsonEncode(body)), status, headers: {'content-type': 'application/json'});

Future<http.Response> problem(int status, String code, String detail, [Map<String, String>? errors]) =>
    json({'status': status, 'code': code, 'detail': detail, 'errors': ?errors}, status);

Map<String, Object?> sessionJson({String role = 'STUDENT', String access = 'access-1', String refresh = 'refresh-1'}) => {
      'accessToken': access,
      'refreshToken': refresh,
      'user': {'id': 1, 'name': role == 'ADMIN' ? 'Head Teacher' : 'Asha', 'mobile': '9876543210', 'role': role},
    };

Session session({String role = 'STUDENT'}) => Session.fromJson(sessionJson(role: role));

Map<String, Object?> myQuizJson({
  int quizId = 1,
  String title = 'Science Week 1',
  String status = 'NEW',
  int? attemptId,
  int? score,
  String? dueAt,
  int questionCount = 2,
}) =>
    {
      'quizId': quizId,
      'title': title,
      'questionCount': questionCount,
      'questionTimeSeconds': 60,
      'totalTimeLimitSeconds': null,
      'assignedAt': '2026-10-01T10:00:00Z',
      'dueAt': dueAt,
      'status': status,
      'attemptId': attemptId,
      'score': score,
    };

Map<String, Object?> questionState(int position, String text, {int secondsLeft = 30}) => {
      'attemptId': 77,
      'quizTitle': 'Science Week 1',
      'status': 'IN_PROGRESS',
      'questionCount': 2,
      'question': {
        'position': position,
        'text': text,
        'options': ['Sun', 'Moon', 'Star', 'Cloud'],
        'timeLimitSeconds': 30,
        'secondsLeft': secondsLeft,
      },
      'quizSecondsLeft': null,
      'result': null,
    };

const finishedState = {
  'attemptId': 77,
  'quizTitle': 'Science Week 1',
  'status': 'COMPLETED',
  'questionCount': 2,
  'question': null,
  'quizSecondsLeft': null,
  'result': {'score': 1, 'questionCount': 2, 'percentage': 50, 'finishReason': 'ALL_ANSWERED'},
};

/// Starts the app against [backend], optionally already logged in.
Future<void> startApp(WidgetTester tester, FakeBackend backend, {Session? loggedIn}) async {
  final client = ApiClient(baseUrl: 'http://test/api', store: MemorySessionStore(loggedIn), httpClient: backend.client);
  final api = QuizApi(client);
  final auth = AuthController(client, api);
  await auth.restore();
  await tester.pumpWidget(QuizApp(api: api, auth: auth));
  await settle(tester);
}

/// Lets requests finish and page transitions complete. (pumpAndSettle never ends while a spinner
/// or countdown is showing.)
Future<void> settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

Future<void> tapText(WidgetTester tester, String text) async {
  final finder = find.text(text);
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await settle(tester);
}

Finder field(String label) => find.widgetWithText(TextField, label);

/// Taps the filled (main) button with this label, when the same words also appear elsewhere.
Future<void> tapButton(WidgetTester tester, String label) async {
  final finder = find.widgetWithText(FilledButton, label);
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await settle(tester);
}
