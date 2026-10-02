import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app.dart';
import '../widgets/common.dart';
import 'review_screen.dart';

/// An attempt as received, with when its timers end on this phone's clock. The server decides
/// what counts; these times only drive the countdown on screen.
class _Running {
  _Running(this.state, DateTime now)
      : questionEndsAt = state.question == null ? null : now.add(Duration(seconds: state.question!.secondsLeft)),
        quizEndsAt = state.quizSecondsLeft == null ? null : now.add(Duration(seconds: state.quizSecondsLeft!));

  final AttemptState state;
  final DateTime? questionEndsAt;
  final DateTime? quizEndsAt;
}

int _secondsUntil(DateTime endsAt) {
  final millis = endsAt.difference(clock.now()).inMilliseconds;
  return millis <= 0 ? 0 : (millis + 999) ~/ 1000;
}

String _clockText(int seconds) => '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';

/// A quiz from introduction to score: questions one at a time, each with its own timer.
class QuizScreen extends StatefulWidget {
  const QuizScreen({super.key, required this.quizId});

  final int quizId;

  @override
  State<QuizScreen> createState() => _QuizScreenState();
}

class _QuizScreenState extends State<QuizScreen> {
  MyQuiz? _quiz;
  _Running? _running;
  String? _error;
  bool _starting = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (_error != null) setState(() => _error = null);
    try {
      final quiz = await AppScope.read(context).api.myQuiz(widget.quizId);
      if (!mounted) return;
      setState(() => _quiz = quiz);
      // A quiz already under way carries on straight away: its timer is running.
      if (quiz.status == MyQuizStatus.inProgress) await _start();
    } catch (e) {
      if (mounted) setState(() => _error = messageFor(e, 'Could not load the quiz.'));
    }
  }

  Future<void> _start() async {
    setState(() {
      _starting = true;
      _error = null;
    });
    try {
      final state = await AppScope.read(context).api.start(widget.quizId);
      if (mounted) setState(() => _running = _Running(state, clock.now()));
    } catch (e) {
      if (mounted) setState(() => _error = messageFor(e, 'Could not start the quiz. Please try again.'));
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  Future<void> _confirmLeave() async {
    final leave = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Leave the quiz?'),
        content: const Text('The timer keeps running while you are away. You can come back and continue.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Leave')),
          FilledButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Stay')),
        ],
      ),
    );
    if (leave == true && mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final running = _running;
    if (running != null && running.state.isFinished) {
      return _Finished(attemptId: running.state.attemptId, title: running.state.quizTitle, result: running.state.result!);
    }
    if (running != null) {
      return PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _confirmLeave();
        },
        child: _QuestionView(
          // Keyed by question so each one starts with nothing selected.
          key: ValueKey(running.state.question!.position),
          running: running,
          onChange: (state) => setState(() => _running = _Running(state, clock.now())),
        ),
      );
    }

    final quiz = _quiz;
    return Scaffold(
      appBar: AppBar(title: Text(quiz?.title ?? 'Quiz')),
      body: SafeArea(
        child: _error != null
            ? ErrorView(message: _error!, onRetry: quiz == null ? _load : _start)
            : quiz == null || quiz.status == MyQuizStatus.inProgress
                ? const Center(child: CircularProgressIndicator())
                : quiz.status == MyQuizStatus.completed
                    ? _AlreadyDone(quiz: quiz)
                    : _Intro(quiz: quiz, starting: _starting, onStart: _start),
      ),
    );
  }
}

class _Intro extends StatelessWidget {
  const _Intro({required this.quiz, required this.starting, required this.onStart});

  final MyQuiz quiz;
  final bool starting;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) {
    final pastDue = quiz.dueAt != null && quiz.dueAt!.isBefore(clock.now());
    Widget rule(IconData icon, String text) => Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: Theme.of(context).colorScheme.primary),
              const SizedBox(width: 12),
              Expanded(child: Text(text)),
            ],
          ),
        );
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        rule(Icons.help_outline, plural(quiz.questionCount, 'question', 'questions')),
        rule(
          Icons.timer_outlined,
          quizTimeText(totalTimeLimitSeconds: quiz.totalTimeLimitSeconds, questionTimeSeconds: quiz.questionTimeSeconds),
        ),
        rule(Icons.touch_app_outlined, 'Each question has its own timer. Choose an answer and tap Submit before the time runs out.'),
        rule(Icons.block, 'You cannot go back to a question.'),
        if (pastDue) ...[
          const MessageBox('This quiz was due earlier. You can still take it.'),
          const SizedBox(height: 16),
        ],
        const SizedBox(height: 8),
        BigButton(label: starting ? 'Starting…' : 'Start quiz', onPressed: starting ? null : onStart),
      ],
    );
  }
}

class _AlreadyDone extends StatelessWidget {
  const _AlreadyDone({required this.quiz});

  final MyQuiz quiz;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          MessageBox('You have finished this quiz. Your score: ${quiz.score} out of ${quiz.questionCount}.', error: false),
          const SizedBox(height: 20),
          BigButton(label: 'Back to my quizzes', onPressed: () => Navigator.of(context).pop()),
        ],
      ),
    );
  }
}

class _QuestionView extends StatefulWidget {
  const _QuestionView({super.key, required this.running, required this.onChange});

  final _Running running;
  final ValueChanged<AttemptState> onChange;

  @override
  State<_QuestionView> createState() => _QuestionViewState();
}

class _QuestionViewState extends State<_QuestionView> {
  int? _selected;
  bool _sending = false;
  String? _problem;
  Timer? _ticker;
  Timer? _timeout;

  @override
  void initState() {
    super.initState();
    _ticker = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (mounted) setState(() {});
    });
    // When time runs out, tell the server so the next question appears. Only a submitted answer counts.
    final endsAt = widget.running.questionEndsAt;
    if (endsAt != null) {
      final wait = endsAt.difference(clock.now());
      _timeout = Timer(wait.isNegative ? Duration.zero : wait, () => _send(null));
    }
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _timeout?.cancel();
    super.dispose();
  }

  Future<void> _send(int? option) async {
    if (_sending) return;
    setState(() {
      _sending = true;
      _problem = null;
    });
    final api = AppScope.read(context).api;
    final state = widget.running.state;
    try {
      widget.onChange(await api.answer(state.attemptId, state.question!.position, option));
    } on ApiException catch (e) {
      if (e.status == 409) {
        // The server has moved on (e.g. the question was already closed): show where we are.
        try {
          widget.onChange(await api.attempt(state.attemptId));
          return;
        } catch (reloadError) {
          if (mounted) setState(() => _problem = messageFor(reloadError, 'Could not send your answer.'));
          return;
        }
      }
      if (mounted) setState(() => _problem = e.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final state = widget.running.state;
    final question = state.question!;
    final left = widget.running.questionEndsAt == null ? 0 : _secondsUntil(widget.running.questionEndsAt!);
    final quizLeft = widget.running.quizEndsAt == null ? null : _secondsUntil(widget.running.quizEndsAt!);
    final timedOut = left == 0;
    final low = left <= 10;
    final locked = _sending || timedOut;

    return Scaffold(
      appBar: AppBar(title: Text('Question ${question.position} of ${state.questionCount}')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            if (quizLeft != null)
              Text('Quiz time left: ${_clockText(quizLeft)}', style: TextStyle(color: scheme.onSurfaceVariant)),
            const SizedBox(height: 8),
            Semantics(
              label: '$left seconds left',
              excludeSemantics: true,
              child: Row(
                children: [
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(10),
                      child: LinearProgressIndicator(
                        value: question.timeLimitSeconds == 0 ? 0 : (left / question.timeLimitSeconds).clamp(0.0, 1.0),
                        minHeight: 14,
                        color: low ? scheme.error : scheme.primary,
                        backgroundColor: scheme.surfaceContainerHighest,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  SizedBox(
                    width: 56,
                    child: Text(
                      '${left}s',
                      textAlign: TextAlign.right,
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: low ? scheme.error : null,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // Read out once, by screen readers, when time is nearly up.
            Semantics(liveRegion: true, label: left == 10 ? '10 seconds left' : '', child: const SizedBox.shrink()),
            const SizedBox(height: 16),
            Semantics(
              header: true,
              child: Text(question.text, style: theme.textTheme.headlineSmall?.copyWith(height: 1.35)),
            ),
            const SizedBox(height: 16),
            for (var i = 0; i < question.options.length; i++)
              _AnswerTile(
                letter: optionLabels[i],
                text: question.options[i],
                selected: _selected == i,
                onTap: locked ? null : () => setState(() => _selected = i),
              ),
            if (_problem != null) ...[
              const SizedBox(height: 8),
              MessageBox(
                _problem!,
                action: FilledButton(
                  onPressed: () => _send(timedOut ? null : _selected),
                  child: const Text('Try again'),
                ),
              ),
            ],
            if (timedOut && _problem == null) ...[
              const SizedBox(height: 8),
              const MessageBox('Time is up! Getting the next question…', error: false),
            ],
            const SizedBox(height: 16),
            BigButton(
              label: _sending ? 'Sending…' : 'Submit',
              onPressed: _selected == null || locked ? null : () => _send(_selected),
            ),
          ],
        ),
      ),
    );
  }
}

class _AnswerTile extends StatelessWidget {
  const _AnswerTile({required this.letter, required this.text, required this.selected, required this.onTap});

  final String letter;
  final String text;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Semantics(
        inMutuallyExclusiveGroup: true,
        checked: selected,
        enabled: onTap != null,
        label: text,
        excludeSemantics: true,
        onTap: onTap,
        child: Material(
          color: selected ? scheme.primaryContainer : scheme.surface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
            side: BorderSide(color: selected ? scheme.primary : scheme.outlineVariant, width: 3),
          ),
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: onTap,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 60),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 18,
                      backgroundColor: selected ? scheme.primary : scheme.surfaceContainerHighest,
                      foregroundColor: selected ? scheme.onPrimary : scheme.onSurface,
                      child: Text(letter, style: const TextStyle(fontWeight: FontWeight.w800)),
                    ),
                    const SizedBox(width: 14),
                    Expanded(child: Text(text, style: const TextStyle(fontSize: 19))),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Finished extends StatelessWidget {
  const _Finished({required this.attemptId, required this.title, required this.result});

  final int attemptId;
  final String title;
  final AttemptResult result;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              result.percentage >= 50 ? 'Well done!' : 'Quiz finished',
              textAlign: TextAlign.center,
              style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 16),
            Semantics(
              label: 'You got ${result.score} out of ${result.questionCount}',
              excludeSemantics: true,
              child: Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '${result.score}',
                      style: TextStyle(fontSize: 72, fontWeight: FontWeight.w800, color: theme.colorScheme.primary),
                    ),
                    TextSpan(text: ' / ${result.questionCount}', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w700)),
                  ],
                ),
                textAlign: TextAlign.center,
              ),
            ),
            Text('${result.percentage}%', textAlign: TextAlign.center, style: theme.textTheme.headlineSmall),
            if (result.timeRanOut)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text('The time for the quiz ran out.', textAlign: TextAlign.center),
              ),
            const SizedBox(height: 28),
            BigButton(
              label: 'See your results',
              onPressed: () => Navigator.of(context)
                  .pushReplacement(MaterialPageRoute(builder: (_) => ReviewScreen(attemptId: attemptId))),
            ),
            const SizedBox(height: 12),
            BigButton(label: 'Back to my quizzes', secondary: true, onPressed: () => Navigator.of(context).pop()),
          ],
        ),
      ),
    );
  }
}
