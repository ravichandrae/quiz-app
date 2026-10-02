import 'api_client.dart';
import 'models.dart';

/// The student's side of the API.
class QuizApi {
  QuizApi(this._client);

  final ApiClient _client;

  Future<Session> login(String mobile, String pin) async {
    final json = await _client.publicPost('/auth/login', {'mobile': mobile, 'pin': pin});
    return Session.fromJson(json as Map<String, dynamic>);
  }

  Future<Session> register({
    required String name,
    required String mobile,
    required String pin,
    String? school,
    String? email,
  }) async {
    final json = await _client.publicPost('/auth/register', {
      'name': name,
      'mobile': mobile,
      'pin': pin,
      if (school != null && school.isNotEmpty) 'school': school,
      if (email != null && email.isNotEmpty) 'email': email,
    });
    return Session.fromJson(json as Map<String, dynamic>);
  }

  Future<void> logout(String refreshToken) => _client.publicPost('/auth/logout', {'refreshToken': refreshToken});

  Future<List<MyQuiz>> myQuizzes() async {
    final json = await _client.get('/me/quizzes') as List;
    return json.map((q) => MyQuiz.fromJson(q as Map<String, dynamic>)).toList();
  }

  Future<MyQuiz> myQuiz(int quizId) async =>
      MyQuiz.fromJson(await _client.get('/me/quizzes/$quizId') as Map<String, dynamic>);

  /// Starts the quiz, or carries on with the attempt already in progress.
  Future<AttemptState> start(int quizId) async =>
      AttemptState.fromJson(await _client.post('/me/quizzes/$quizId/attempt') as Map<String, dynamic>);

  Future<AttemptState> attempt(int attemptId) async =>
      AttemptState.fromJson(await _client.get('/me/attempts/$attemptId') as Map<String, dynamic>);

  /// [selectedOption] is null when the time ran out without an answer.
  Future<AttemptState> answer(int attemptId, int position, int? selectedOption) async {
    final json = await _client.post(
      '/me/attempts/$attemptId/answers',
      {'position': position, 'selectedOption': selectedOption},
    );
    return AttemptState.fromJson(json as Map<String, dynamic>);
  }

  Future<List<MyResult>> myResults() async {
    final json = await _client.get('/me/results') as List;
    return json.map((r) => MyResult.fromJson(r as Map<String, dynamic>)).toList();
  }

  Future<MyReview> myReview(int attemptId) async =>
      MyReview.fromJson(await _client.get('/me/results/$attemptId') as Map<String, dynamic>);
}
