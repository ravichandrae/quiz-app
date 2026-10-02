/// Data returned by the Schoolmela Quiz API (only what the student app needs).
library;

class SessionUser {
  const SessionUser({required this.id, required this.name, required this.mobile, required this.role});

  factory SessionUser.fromJson(Map<String, dynamic> json) => SessionUser(
        id: json['id'] as int,
        name: json['name'] as String,
        mobile: json['mobile'] as String,
        role: json['role'] as String,
      );

  final int id;
  final String name;
  final String mobile;

  /// `STUDENT` or `ADMIN`.
  final String role;

  bool get isStudent => role == 'STUDENT';

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'mobile': mobile, 'role': role};
}

class Session {
  const Session({required this.accessToken, required this.refreshToken, required this.user});

  factory Session.fromJson(Map<String, dynamic> json) => Session(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        user: SessionUser.fromJson(json['user'] as Map<String, dynamic>),
      );

  final String accessToken;
  final String refreshToken;
  final SessionUser user;

  Map<String, dynamic> toJson() => {'accessToken': accessToken, 'refreshToken': refreshToken, 'user': user.toJson()};
}

enum MyQuizStatus { newQuiz, inProgress, completed }

MyQuizStatus _status(String value) => switch (value) {
      'IN_PROGRESS' => MyQuizStatus.inProgress,
      'COMPLETED' => MyQuizStatus.completed,
      _ => MyQuizStatus.newQuiz,
    };

DateTime? _date(Object? value) => value == null ? null : DateTime.parse(value as String);

/// A quiz on the student's list.
class MyQuiz {
  const MyQuiz({
    required this.quizId,
    required this.title,
    required this.questionCount,
    required this.questionTimeSeconds,
    required this.totalTimeLimitSeconds,
    required this.dueAt,
    required this.status,
    required this.attemptId,
    required this.score,
  });

  factory MyQuiz.fromJson(Map<String, dynamic> json) => MyQuiz(
        quizId: json['quizId'] as int,
        title: json['title'] as String,
        questionCount: json['questionCount'] as int,
        questionTimeSeconds: json['questionTimeSeconds'] as int,
        totalTimeLimitSeconds: json['totalTimeLimitSeconds'] as int?,
        dueAt: _date(json['dueAt']),
        status: _status(json['status'] as String),
        attemptId: json['attemptId'] as int?,
        score: json['score'] as int?,
      );

  final int quizId;
  final String title;
  final int questionCount;
  final int questionTimeSeconds;
  final int? totalTimeLimitSeconds;
  final DateTime? dueAt;
  final MyQuizStatus status;
  final int? attemptId;

  /// Set once the quiz is completed.
  final int? score;
}

class CurrentQuestion {
  const CurrentQuestion({
    required this.position,
    required this.text,
    required this.options,
    required this.timeLimitSeconds,
    required this.secondsLeft,
  });

  factory CurrentQuestion.fromJson(Map<String, dynamic> json) => CurrentQuestion(
        position: json['position'] as int,
        text: json['text'] as String,
        options: (json['options'] as List).cast<String>(),
        timeLimitSeconds: json['timeLimitSeconds'] as int,
        secondsLeft: json['secondsLeft'] as int,
      );

  /// 1-based.
  final int position;
  final String text;
  final List<String> options;
  final int timeLimitSeconds;
  final int secondsLeft;
}

class AttemptResult {
  const AttemptResult({
    required this.score,
    required this.questionCount,
    required this.percentage,
    required this.timeRanOut,
  });

  factory AttemptResult.fromJson(Map<String, dynamic> json) => AttemptResult(
        score: json['score'] as int,
        questionCount: json['questionCount'] as int,
        percentage: json['percentage'] as int,
        timeRanOut: json['finishReason'] == 'TIME_UP',
      );

  final int score;
  final int questionCount;
  final int percentage;

  /// The quiz's overall time ran out before every question was answered.
  final bool timeRanOut;
}

/// Where the student is in a quiz: the question to answer now, or the result once it is over.
class AttemptState {
  const AttemptState({
    required this.attemptId,
    required this.quizTitle,
    required this.questionCount,
    required this.question,
    required this.quizSecondsLeft,
    required this.result,
  });

  factory AttemptState.fromJson(Map<String, dynamic> json) => AttemptState(
        attemptId: json['attemptId'] as int,
        quizTitle: json['quizTitle'] as String,
        questionCount: json['questionCount'] as int,
        question: json['question'] == null
            ? null
            : CurrentQuestion.fromJson(json['question'] as Map<String, dynamic>),
        quizSecondsLeft: json['quizSecondsLeft'] as int?,
        result: json['result'] == null ? null : AttemptResult.fromJson(json['result'] as Map<String, dynamic>),
      );

  final int attemptId;
  final String quizTitle;
  final int questionCount;
  final CurrentQuestion? question;
  final int? quizSecondsLeft;
  final AttemptResult? result;

  bool get isFinished => result != null;
}

/// A finished quiz in the student's score history.
class MyResult {
  const MyResult({
    required this.attemptId,
    required this.quizTitle,
    required this.finishedAt,
    required this.score,
    required this.questionCount,
    required this.percentage,
  });

  factory MyResult.fromJson(Map<String, dynamic> json) => MyResult(
        attemptId: json['attemptId'] as int,
        quizTitle: json['quizTitle'] as String,
        finishedAt: DateTime.parse(json['finishedAt'] as String),
        score: json['score'] as int,
        questionCount: json['questionCount'] as int,
        percentage: json['percentage'] as int,
      );

  final int attemptId;
  final String quizTitle;
  final DateTime finishedAt;
  final int score;
  final int questionCount;
  final int percentage;
}

enum Outcome { correct, wrong, noAnswer, notReached }

class ReviewQuestion {
  const ReviewQuestion({
    required this.position,
    required this.text,
    required this.options,
    required this.selectedOption,
    required this.correctOption,
    required this.outcome,
  });

  factory ReviewQuestion.fromJson(Map<String, dynamic> json) => ReviewQuestion(
        position: json['position'] as int,
        text: json['text'] as String,
        options: (json['options'] as List).cast<String>(),
        selectedOption: json['selectedOption'] as int?,
        correctOption: json['correctOption'] as int,
        outcome: switch (json['outcome']) {
          'CORRECT' => Outcome.correct,
          'WRONG' => Outcome.wrong,
          'NO_ANSWER' => Outcome.noAnswer,
          _ => Outcome.notReached,
        },
      );

  final int position;
  final String text;
  final List<String> options;
  final int? selectedOption;
  final int correctOption;
  final Outcome outcome;
}

/// A finished attempt; [questions] is empty when the teacher hides the answers.
class MyReview {
  const MyReview({
    required this.quizTitle,
    required this.score,
    required this.questionCount,
    required this.percentage,
    required this.timeRanOut,
    required this.answersShown,
    required this.questions,
  });

  factory MyReview.fromJson(Map<String, dynamic> json) => MyReview(
        quizTitle: json['quizTitle'] as String,
        score: json['score'] as int,
        questionCount: json['questionCount'] as int,
        percentage: json['percentage'] as int,
        timeRanOut: json['finishReason'] == 'TIME_UP',
        answersShown: json['answersShown'] as bool,
        questions: (json['questions'] as List)
            .map((q) => ReviewQuestion.fromJson(q as Map<String, dynamic>))
            .toList(),
      );

  final String quizTitle;
  final int score;
  final int questionCount;
  final int percentage;
  final bool timeRanOut;
  final bool answersShown;
  final List<ReviewQuestion> questions;
}
