import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'support.dart';

void main() {
  testWidgets('home shows what to do next with each quiz', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes': (_) => json([
            myQuizJson(quizId: 1, title: 'Science', dueAt: '2099-10-05T18:29:59Z'),
            myQuizJson(quizId: 2, title: 'Maths', status: 'IN_PROGRESS', attemptId: 5),
            myQuizJson(quizId: 3, title: 'History', status: 'COMPLETED', attemptId: 6, score: 8, questionCount: 10),
          ]),
    });
    await startApp(tester, backend, loggedIn: session());

    expect(find.text('You have 1 new quiz!'), findsOneWidget);
    expect(find.text('New'), findsOneWidget);
    expect(find.text('Start'), findsOneWidget);
    expect(find.textContaining('Finish by'), findsOneWidget);
    expect(find.text('Continue'), findsOneWidget);
    expect(find.text('Your score: 8 out of 10'), findsOneWidget);
    expect(find.text('See results'), findsOneWidget);
  });

  testWidgets('takes a quiz from the introduction to the score and the review', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes/1': (_) => json(myQuizJson()),
      'GET /me/quizzes': (_) => json([myQuizJson()]),
      'POST /me/quizzes/1/attempt': (_) => json(questionState(1, 'What gives us light in the day?')),
      'POST /me/attempts/77/answers': (request) =>
          request.body.contains('"position":1') ? json(questionState(2, 'What shines at night?')) : json(finishedState),
      'GET /me/results/77': (_) => json({
            'attemptId': 77,
            'quizId': 1,
            'quizTitle': 'Science Week 1',
            'finishedAt': '2026-10-01T10:00:00Z',
            'score': 1,
            'questionCount': 2,
            'percentage': 50,
            'finishReason': 'ALL_ANSWERED',
            'answersShown': true,
            'questions': [
              {'position': 1, 'text': 'What gives us light in the day?', 'options': ['Sun', 'Moon', 'Star', 'Cloud'], 'selectedOption': 0, 'correctOption': 0, 'outcome': 'CORRECT'},
              {'position': 2, 'text': 'What shines at night?', 'options': ['Sun', 'Moon', 'Star', 'Cloud'], 'selectedOption': 3, 'correctOption': 1, 'outcome': 'WRONG'},
            ],
          }),
    });
    await startApp(tester, backend, loggedIn: session());

    await tapText(tester, 'Start');
    expect(find.text('You cannot go back to a question.'), findsOneWidget);
    await tapText(tester, 'Start quiz');

    expect(find.text('Question 1 of 2'), findsOneWidget);
    expect(find.text('What gives us light in the day?'), findsOneWidget);
    expect(find.text('30s'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Submit')).onPressed, isNull);
    await tapText(tester, 'Sun');
    await tapText(tester, 'Submit');

    expect(find.text('Question 2 of 2'), findsOneWidget);
    await tapText(tester, 'Cloud');
    await tapText(tester, 'Submit');

    expect(find.text('Well done!'), findsOneWidget);
    expect(find.bySemanticsLabel('You got 1 out of 2'), findsOneWidget);
    expect(find.text('50%'), findsOneWidget);
    expect(backend.bodiesTo('/answers'), [
      {'position': 1, 'selectedOption': 0},
      {'position': 2, 'selectedOption': 3},
    ]);

    await tapText(tester, 'See your results');
    expect(find.text('Right'), findsOneWidget);
    expect(find.text('Wrong'), findsOneWidget);
    expect(find.text('(your answer, correct answer)'), findsOneWidget);
    expect(find.text('(correct answer)'), findsOneWidget);
  });

  testWidgets('moves on without an answer when the time runs out', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes/1': (_) => json(myQuizJson()),
      'GET /me/quizzes': (_) => json([myQuizJson()]),
      'POST /me/quizzes/1/attempt': (_) => json(questionState(1, 'Quick one', secondsLeft: 5)),
      'POST /me/attempts/77/answers': (_) => json(questionState(2, 'Next one')),
    });
    await startApp(tester, backend, loggedIn: session());
    await tapText(tester, 'Start');
    await tapText(tester, 'Start quiz');
    // Choosing without submitting does not count.
    await tapText(tester, 'Moon');

    // About a second has passed: the question is still open.
    expect(find.text('Quick one'), findsOneWidget);
    expect(backend.bodiesTo('/answers'), isEmpty);

    await tester.pump(const Duration(seconds: 4));
    await settle(tester);

    expect(find.text('Next one'), findsOneWidget);
    expect(backend.bodiesTo('/answers'), [
      {'position': 1, 'selectedOption': null},
    ]);
  });

  testWidgets('asks before leaving a quiz in progress', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes/1': (_) => json(myQuizJson(status: 'IN_PROGRESS', attemptId: 77)),
      'GET /me/quizzes': (_) => json([myQuizJson(status: 'IN_PROGRESS', attemptId: 77)]),
      'POST /me/quizzes/1/attempt': (_) => json(questionState(2, 'Where we left off', secondsLeft: 20)),
    });
    await startApp(tester, backend, loggedIn: session());

    await tapText(tester, 'Continue');
    // A quiz in progress resumes without the introduction.
    expect(find.text('Where we left off'), findsOneWidget);

    await tester.pageBack();
    await settle(tester);
    expect(find.text('Leave the quiz?'), findsOneWidget);
    await tapText(tester, 'Stay');
    expect(find.text('Where we left off'), findsOneWidget);

    await tester.pageBack();
    await settle(tester);
    await tapText(tester, 'Leave');
    expect(find.text('Hello, Asha!'), findsOneWidget);
  });

  testWidgets('keeps the chosen answer and offers to try again when the network fails', (tester) async {
    var online = false;
    final backend = FakeBackend({
      'GET /me/quizzes/1': (_) => json(myQuizJson()),
      'GET /me/quizzes': (_) => json([myQuizJson()]),
      'POST /me/quizzes/1/attempt': (_) => json(questionState(1, 'First')),
      'POST /me/attempts/77/answers': (_) =>
          online ? json(questionState(2, 'Second')) : Future.error(http.ClientException('offline')),
    });
    await startApp(tester, backend, loggedIn: session());
    await tapText(tester, 'Start');
    await tapText(tester, 'Start quiz');
    await tapText(tester, 'Star');
    await tapText(tester, 'Submit');

    expect(find.text('No internet connection. Please try again.'), findsOneWidget);

    online = true;
    await tapText(tester, 'Try again');
    expect(find.text('Second'), findsOneWidget);
    expect(backend.bodiesTo('/answers').last, {'position': 1, 'selectedOption': 2});
  });

  testWidgets('shows only the score when the teacher hides the answers', (tester) async {
    final backend = FakeBackend({
      'GET /me/quizzes': (_) => json([]),
      'GET /me/results/9': (_) => json({
            'attemptId': 9,
            'quizId': 1,
            'quizTitle': 'Maths',
            'finishedAt': '2026-10-01T10:00:00Z',
            'score': 7,
            'questionCount': 10,
            'percentage': 70,
            'finishReason': 'TIME_UP',
            'answersShown': false,
            'questions': [],
          }),
      'GET /me/results': (_) => json([
            {'attemptId': 9, 'quizId': 1, 'quizTitle': 'Maths', 'finishedAt': '2026-10-01T10:00:00Z', 'score': 7, 'questionCount': 10, 'percentage': 70},
          ]),
    });
    await startApp(tester, backend, loggedIn: session());

    await tapText(tester, 'My scores');
    expect(find.text('70%'), findsOneWidget);
    await tapText(tester, 'Maths');

    expect(find.text('Your teacher will go through the answers with you.'), findsOneWidget);
    expect(find.text('The time for the quiz ran out.'), findsOneWidget);
  });
}
