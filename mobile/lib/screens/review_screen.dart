import 'package:flutter/material.dart';

import '../api/models.dart';
import '../app.dart';
import '../widgets/common.dart';

/// The score of a finished quiz, and each answer if the teacher allows it.
class ReviewScreen extends StatefulWidget {
  const ReviewScreen({super.key, required this.attemptId});

  final int attemptId;

  @override
  State<ReviewScreen> createState() => _ReviewScreenState();
}

class _ReviewScreenState extends State<ReviewScreen> {
  late Future<MyReview> _future = AppScope.read(context).api.myReview(widget.attemptId);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Your results')),
      body: SafeArea(
        child: FutureBuilder<MyReview>(
          future: _future,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return ErrorView(
                message: messageFor(snapshot.error!, 'Could not load your results.'),
                onRetry: () => setState(() {
                  _future = AppScope.read(context).api.myReview(widget.attemptId);
                }),
              );
            }
            final review = snapshot.data;
            if (review == null) return const Center(child: CircularProgressIndicator());
            final theme = Theme.of(context);
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(review.quizTitle, style: theme.textTheme.titleLarge),
                const SizedBox(height: 8),
                Semantics(
                  label: 'You got ${review.score} out of ${review.questionCount}, ${review.percentage} percent',
                  excludeSemantics: true,
                  child: Text(
                    '${review.score} / ${review.questionCount}   ${review.percentage}%',
                    style: theme.textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ),
                if (review.timeRanOut) const Text('The time for the quiz ran out.'),
                const SizedBox(height: 16),
                if (!review.answersShown)
                  const MessageBox('Your teacher will go through the answers with you.', error: false)
                else
                  for (final q in review.questions) _QuestionResult(question: q),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _QuestionResult extends StatelessWidget {
  const _QuestionResult({required this.question});

  final ReviewQuestion question;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final (label, icon, good) = switch (question.outcome) {
      Outcome.correct => ('Right', Icons.check_circle, true),
      Outcome.wrong => ('Wrong', Icons.cancel, false),
      Outcome.noAnswer => ('Time ran out', Icons.timer_off, false),
      Outcome.notReached => ('Not reached', Icons.remove_circle_outline, false),
    };
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${question.position}. ${question.text}', style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Row(
              children: [
                Icon(icon, color: good ? Colors.green.shade700 : scheme.error),
                const SizedBox(width: 6),
                Text(label, style: TextStyle(fontWeight: FontWeight.w700, color: good ? Colors.green.shade700 : scheme.error)),
              ],
            ),
            const SizedBox(height: 8),
            for (var i = 0; i < question.options.length; i++) _option(context, i),
          ],
        ),
      ),
    );
  }

  Widget _option(BuildContext context, int i) {
    final scheme = Theme.of(context).colorScheme;
    final chosen = question.selectedOption == i;
    final correct = question.correctOption == i;
    final notes = [if (chosen) 'your answer', if (correct) 'correct answer'].join(', ');
    final border = correct ? Colors.green.shade700 : (chosen ? scheme.error : Colors.transparent);
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        border: Border.all(color: border, width: 2),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Text('${optionLabels[i]}. ', style: const TextStyle(fontWeight: FontWeight.w800)),
          Expanded(child: Text(question.options[i])),
          if (notes.isNotEmpty) Text('($notes)', style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 14)),
        ],
      ),
    );
  }
}
