import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:schoolmela_quiz/auth/session_store.dart';
import 'package:schoolmela_quiz/main.dart' as app;

/// End-to-end run against a real server (by default the local docker compose stack, reached from
/// the emulator at 10.0.2.2). It needs a student with an assigned, untaken quiz:
///
///   flutter drive --driver=test_driver/integration_test.dart --target=integration_test/app_test.dart \
///     --dart-define=TEST_MOBILE=9123456781 --dart-define=TEST_PIN=2468 \
///     --dart-define=TEST_QUIZ="Emulator Test Quiz"
const mobile = String.fromEnvironment('TEST_MOBILE');
const pin = String.fromEnvironment('TEST_PIN');
const quizTitle = String.fromEnvironment('TEST_QUIZ');

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  /// Waits (in real time) until [finder] shows something.
  Future<void> waitFor(WidgetTester tester, Finder finder, {Duration timeout = const Duration(seconds: 20)}) async {
    final end = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(end)) {
      await tester.pump(const Duration(milliseconds: 200));
      if (finder.evaluate().isNotEmpty) return;
    }
    throw TestFailure('Timed out waiting for $finder');
  }

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await waitFor(tester, finder);
    await tester.ensureVisible(finder.first);
    await tester.tap(finder.first);
    await tester.pump(const Duration(milliseconds: 500));
  }

  Future<void> screenshot(WidgetTester tester, String name) async {
    await tester.pump(const Duration(milliseconds: 600));
    await binding.takeScreenshot(name);
  }

  testWidgets('a student logs in, takes a quiz and reviews the answers', (tester) async {
    expect(mobile, isNotEmpty, reason: 'Pass --dart-define=TEST_MOBILE=...');
    // Start logged out, whatever an earlier run left behind.
    await SecureSessionStore().write(null);
    app.main();
    await binding.convertFlutterSurfaceToImage();

    await waitFor(tester, find.text('Mobile number'));
    await screenshot(tester, '1-login');
    await tester.enterText(find.widgetWithText(TextField, 'Mobile number'), mobile);
    await tester.enterText(find.widgetWithText(TextField, 'PIN'), pin);
    await tap(tester, find.widgetWithText(FilledButton, 'Log in'));

    await waitFor(tester, find.text(quizTitle));
    await screenshot(tester, '2-home');
    final card = find.ancestor(of: find.text(quizTitle), matching: find.byType(Card));
    await tap(tester, find.descendant(of: card, matching: find.widgetWithText(FilledButton, 'Start')));

    await waitFor(tester, find.text('Start quiz'));
    await screenshot(tester, '3-intro');
    await tap(tester, find.text('Start quiz'));

    await waitFor(tester, find.text('Question 1 of 2'));
    await tap(tester, find.text('Earth'));
    await screenshot(tester, '4-question');
    await tap(tester, find.widgetWithText(FilledButton, 'Submit'));

    await waitFor(tester, find.text('Question 2 of 2'));
    await tap(tester, find.text('Six'));
    await tap(tester, find.widgetWithText(FilledButton, 'Submit'));

    await waitFor(tester, find.text('See your results'));
    expect(find.bySemanticsLabel('You got 1 out of 2'), findsOneWidget);
    await screenshot(tester, '5-finished');

    await tap(tester, find.text('See your results'));
    await waitFor(tester, find.text('Right'));
    expect(find.text('Wrong'), findsOneWidget);
    await screenshot(tester, '6-review');
  });
}
