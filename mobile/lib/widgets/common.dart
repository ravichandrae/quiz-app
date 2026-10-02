import 'package:flutter/material.dart';

import '../api/api_client.dart';

const optionLabels = ['A', 'B', 'C', 'D'];

/// "45 sec", "2 min", "1 min 30 sec".
String formatDuration(int totalSeconds) {
  final minutes = totalSeconds ~/ 60;
  final seconds = totalSeconds % 60;
  if (minutes == 0) return '$seconds sec';
  return seconds == 0 ? '$minutes min' : '$minutes min $seconds sec';
}

/// "5 min" for a quiz with an overall limit, otherwise "Up to 2 min 30 sec".
String quizTimeText({required int? totalTimeLimitSeconds, required int questionTimeSeconds}) =>
    totalTimeLimitSeconds == null ? 'Up to ${formatDuration(questionTimeSeconds)}' : formatDuration(totalTimeLimitSeconds);

String plural(int count, String one, String many) => '$count ${count == 1 ? one : many}';

const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const _weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/// "Sat, 5 Oct" in the phone's time zone.
String formatDay(DateTime date) {
  final local = date.toLocal();
  return '${_weekdays[local.weekday - 1]}, ${local.day} ${_months[local.month - 1]}';
}

/// The message to show for any error; API errors are already written for students.
String messageFor(Object error, String fallback) => error is ApiException ? error.message : fallback;

/// A full-width button with a large tap target.
class BigButton extends StatelessWidget {
  const BigButton({super.key, required this.label, required this.onPressed, this.secondary = false});

  final String label;
  final VoidCallback? onPressed;
  final bool secondary;

  @override
  Widget build(BuildContext context) {
    final child = Text(label);
    return SizedBox(
      width: double.infinity,
      child: secondary
          ? OutlinedButton(onPressed: onPressed, child: child)
          : FilledButton(onPressed: onPressed, child: child),
    );
  }
}

/// A coloured box with a message, used for errors and notices.
class MessageBox extends StatelessWidget {
  const MessageBox(this.message, {super.key, this.error = true, this.action});

  final String message;
  final bool error;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: error ? scheme.errorContainer : scheme.primaryContainer,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              message,
              style: TextStyle(
                fontWeight: FontWeight.w600,
                color: error ? scheme.onErrorContainer : scheme.onPrimaryContainer,
              ),
            ),
            if (action != null) ...[const SizedBox(height: 10), action!],
          ],
        ),
      ),
    );
  }
}

/// Shown when something could not be loaded, with a button to try again.
class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: MessageBox(
          message,
          action: OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
        ),
      ),
    );
  }
}

/// A small coloured label such as "New" or "Done".
class Tag extends StatelessWidget {
  const Tag(this.label, {super.key, required this.color, required this.textColor});

  final String label;
  final Color color;
  final Color textColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20)),
      child: Text(label, style: TextStyle(color: textColor, fontWeight: FontWeight.w700, fontSize: 14)),
    );
  }
}
