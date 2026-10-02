import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:schoolmela_quiz/screens/register_screen.dart';

import 'support.dart';

/// A field on the register screen (the login screen stays mounted underneath it).
Finder registerField(String label) => find.descendant(of: find.byType(RegisterScreen), matching: field(label));

void main() {
  testWidgets('logs in with a digits-only mobile number and shows the quizzes', (tester) async {
    final backend = FakeBackend({
      'POST /auth/login': (_) => json(sessionJson()),
      'GET /me/quizzes': (_) => json([myQuizJson()]),
    });
    await startApp(tester, backend);

    await tester.enterText(field('Mobile number'), '98765 43210');
    await tester.enterText(field('PIN'), '4321');
    await tapButton(tester, 'Log in');

    expect(find.text('Hello, Asha!'), findsOneWidget);
    expect(find.text('Science Week 1'), findsOneWidget);
    expect(backend.bodiesTo('/auth/login'), [
      {'mobile': '9876543210', 'pin': '4321'},
    ]);
  });

  testWidgets('shows the server message for a wrong PIN', (tester) async {
    final backend = FakeBackend({
      'POST /auth/login': (_) => problem(401, 'INVALID_CREDENTIALS', 'Wrong mobile number or PIN. Please try again.'),
    });
    await startApp(tester, backend);

    await tester.enterText(field('Mobile number'), '9876543210');
    await tester.enterText(field('PIN'), '0000');
    await tapButton(tester, 'Log in');

    expect(find.text('Wrong mobile number or PIN. Please try again.'), findsOneWidget);
    expect(find.text('Hello, Asha!'), findsNothing);
  });

  testWidgets('the PIN box accepts only digits', (tester) async {
    await startApp(tester, FakeBackend({}));

    await tester.enterText(field('PIN'), '12ab34');

    expect(tester.widget<TextField>(field('PIN')).controller!.text, '1234');
  });

  testWidgets('registers and goes to the home screen', (tester) async {
    final backend = FakeBackend({
      'POST /auth/register': (_) => json(sessionJson(), 201),
      'GET /me/quizzes': (_) => json([]),
    });
    await startApp(tester, backend);
    await tapText(tester, 'New here? Create an account');

    await tester.enterText(registerField('Your name'), 'Asha');
    await tester.enterText(registerField('Mobile number'), '9876543210');
    await tester.enterText(registerField('Choose a PIN'), '4321');
    await tester.enterText(registerField('Type your PIN again'), '4321');
    await tapText(tester, 'Create account');

    expect(find.text('Hello, Asha!'), findsOneWidget);
    expect(find.text('You have no quizzes yet. Your teacher will add them soon.'), findsOneWidget);
    expect(backend.bodiesTo('/auth/register'), [
      {'name': 'Asha', 'mobile': '9876543210', 'pin': '4321'},
    ]);
  });

  testWidgets('registration shows errors next to the fields', (tester) async {
    final backend = FakeBackend({
      'POST /auth/register': (_) => problem(400, 'VALIDATION_FAILED', 'Please check the details you entered.',
          {'mobile': 'Mobile number must be 10 digits'}),
    });
    await startApp(tester, backend);
    await tapText(tester, 'New here? Create an account');

    await tester.enterText(registerField('Choose a PIN'), '4321');
    await tester.enterText(registerField('Type your PIN again'), '1234');
    await tapText(tester, 'Create account');
    expect(find.text('The two PINs are not the same'), findsOneWidget);
    expect(backend.requests, isEmpty);

    await tester.enterText(registerField('Type your PIN again'), '4321');
    await tapText(tester, 'Create account');
    expect(find.text('Mobile number must be 10 digits'), findsOneWidget);
  });

  testWidgets('a saved login opens the home screen straight away, and Log out forgets it', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes': (_) => json([]),
      'POST /auth/logout': (_) => json(null, 204),
    });
    await startApp(tester, backend, loggedIn: session());
    expect(find.text('Hello, Asha!'), findsOneWidget);

    await tapText(tester, 'Log out');

    expect(find.text('Log in'), findsWidgets);
    expect(backend.bodiesTo('/auth/logout'), [
      {'refreshToken': 'refresh-1'},
    ]);
  });

  testWidgets('teachers are sent to the website', (tester) async {
    await startApp(tester, FakeBackend({}), loggedIn: session(role: 'ADMIN'));

    expect(find.textContaining('Teachers, please use the Schoolmela Quiz website'), findsOneWidget);
  });

  testWidgets('an expired login goes back to the login screen', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes': (_) => problem(401, 'UNAUTHORIZED', ''),
      'POST /auth/refresh': (_) => problem(401, 'SESSION_EXPIRED', 'Please log in again.'),
    });
    await startApp(tester, backend, loggedIn: session());

    expect(find.widgetWithText(FilledButton, 'Log in'), findsOneWidget);
  });
}
