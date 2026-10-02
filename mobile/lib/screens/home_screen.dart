import 'package:flutter/material.dart';

import '../api/models.dart';
import '../app.dart';
import '../widgets/common.dart';
import 'quiz_screen.dart';
import 'review_screen.dart';

/// The student's start screen: their quizzes, and their scores.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final scope = AppScope.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text('Hello, ${scope.auth.user?.name ?? ''}!'),
        actions: [
          TextButton.icon(
            onPressed: scope.auth.logout,
            icon: const Icon(Icons.logout),
            label: const Text('Log out'),
          ),
        ],
      ),
      body: SafeArea(child: _tab == 0 ? const _QuizList() : const _ScoreList()),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.assignment_outlined), selectedIcon: Icon(Icons.assignment), label: 'My quizzes'),
          NavigationDestination(icon: Icon(Icons.star_outline), selectedIcon: Icon(Icons.star), label: 'My scores'),
        ],
      ),
    );
  }
}

/// Loads a list, with pull-to-refresh and a retry button on errors.
class _Loader<T> extends StatefulWidget {
  const _Loader({required this.load, required this.builder, required this.errorMessage});

  final Future<List<T>> Function() load;
  final Widget Function(BuildContext context, List<T> items, VoidCallback reload) builder;
  final String errorMessage;

  @override
  State<_Loader<T>> createState() => _LoaderState<T>();
}

class _LoaderState<T> extends State<_Loader<T>> {
  late Future<List<T>> _future = widget.load();

  void _reload() => setState(() {
        _future = widget.load();
      });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<T>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return ErrorView(message: messageFor(snapshot.error!, widget.errorMessage), onRetry: _reload);
        }
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        return RefreshIndicator(
          onRefresh: () async {
            _reload();
            await _future.catchError((_) => <T>[]);
          },
          child: widget.builder(context, snapshot.data!, _reload),
        );
      },
    );
  }
}

class _QuizList extends StatelessWidget {
  const _QuizList();

  @override
  Widget build(BuildContext context) {
    final api = AppScope.of(context).api;
    return _Loader<MyQuiz>(
      load: api.myQuizzes,
      errorMessage: 'Could not load your quizzes.',
      builder: (context, quizzes, reload) {
        final newCount = quizzes.where((q) => q.status == MyQuizStatus.newQuiz).length;
        return ListView(
          padding: const EdgeInsets.all(16),
          // Always scrollable, so pull-to-refresh works on short lists too.
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            if (quizzes.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 40),
                child: Text('You have no quizzes yet. Your teacher will add them soon.', textAlign: TextAlign.center),
              ),
            if (newCount > 0) ...[
              MessageBox('You have ${plural(newCount, 'new quiz', 'new quizzes')}!', error: false),
              const SizedBox(height: 12),
            ],
            for (final quiz in quizzes) _QuizCard(quiz: quiz, onChanged: reload),
          ],
        );
      },
    );
  }
}

class _QuizCard extends StatelessWidget {
  const _QuizCard({required this.quiz, required this.onChanged});

  final MyQuiz quiz;
  final VoidCallback onChanged;

  Future<void> _open(BuildContext context, Widget screen) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    // The quiz may have been started or finished meanwhile.
    onChanged();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final done = quiz.status == MyQuizStatus.completed;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: done ? scheme.outlineVariant : scheme.primary, width: 2),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(quiz.title, style: Theme.of(context).textTheme.titleLarge)),
                if (quiz.status == MyQuizStatus.newQuiz)
                  Tag('New', color: scheme.tertiary, textColor: scheme.onTertiary),
                if (done) Tag('Done', color: scheme.primaryContainer, textColor: scheme.onPrimaryContainer),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 16,
              runSpacing: 4,
              children: [
                _Fact(icon: Icons.help_outline, text: plural(quiz.questionCount, 'question', 'questions')),
                _Fact(
                  icon: Icons.timer_outlined,
                  text: quizTimeText(
                    totalTimeLimitSeconds: quiz.totalTimeLimitSeconds,
                    questionTimeSeconds: quiz.questionTimeSeconds,
                  ),
                ),
                if (quiz.dueAt != null && !done)
                  _Fact(icon: Icons.event_outlined, text: 'Finish by ${formatDay(quiz.dueAt!)}'),
              ],
            ),
            const SizedBox(height: 12),
            if (done) ...[
              Text(
                'Your score: ${quiz.score} out of ${quiz.questionCount}',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              BigButton(
                label: 'See results',
                secondary: true,
                onPressed: () => _open(context, ReviewScreen(attemptId: quiz.attemptId!)),
              ),
            ] else
              BigButton(
                label: quiz.status == MyQuizStatus.newQuiz ? 'Start' : 'Continue',
                onPressed: () => _open(context, QuizScreen(quizId: quiz.quizId)),
              ),
          ],
        ),
      ),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 20, color: Theme.of(context).colorScheme.onSurfaceVariant),
        const SizedBox(width: 4),
        Text(text),
      ],
    );
  }
}

class _ScoreList extends StatelessWidget {
  const _ScoreList();

  @override
  Widget build(BuildContext context) {
    final api = AppScope.of(context).api;
    return _Loader<MyResult>(
      load: api.myResults,
      errorMessage: 'Could not load your scores.',
      builder: (context, results, _) => ListView(
        padding: const EdgeInsets.all(16),
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          if (results.isEmpty)
            const Padding(
              padding: EdgeInsets.only(top: 40),
              child: Text('You have not finished any quizzes yet.', textAlign: TextAlign.center),
            ),
          for (final r in results)
            Card(
              margin: const EdgeInsets.only(bottom: 12),
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                title: Text(r.quizTitle, style: Theme.of(context).textTheme.titleMedium),
                subtitle: Text('Score: ${r.score} out of ${r.questionCount} · ${formatDay(r.finishedAt)}'),
                trailing: Text(
                  '${r.percentage}%',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
                onTap: () => Navigator.of(context)
                    .push(MaterialPageRoute(builder: (_) => ReviewScreen(attemptId: r.attemptId))),
              ),
            ),
        ],
      ),
    );
  }
}
