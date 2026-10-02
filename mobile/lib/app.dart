import 'package:flutter/material.dart';

import 'api/quiz_api.dart';
import 'auth/auth_controller.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'widgets/common.dart';

/// Gives screens access to the API and the login.
class AppScope extends InheritedWidget {
  const AppScope({super.key, required this.api, required this.auth, required super.child});

  final QuizApi api;
  final AuthController auth;

  static AppScope of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<AppScope>()!;

  /// Like [of] without rebuilding on changes; usable in initState and callbacks.
  static AppScope read(BuildContext context) => context.getInheritedWidgetOfExactType<AppScope>()!;

  @override
  bool updateShouldNotify(AppScope oldWidget) => api != oldWidget.api || auth != oldWidget.auth;
}

const _green = Color(0xFF15803D);

ThemeData _theme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(seedColor: _green, brightness: brightness);
  final base = ThemeData(colorScheme: scheme, useMaterial3: true);
  const buttonSize = Size(64, 56);
  const buttonText = TextStyle(fontSize: 18, fontWeight: FontWeight.w700);
  final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
  return base.copyWith(
    // Larger text for young and new readers.
    textTheme: base.textTheme.copyWith(
      bodyLarge: base.textTheme.bodyLarge?.copyWith(fontSize: 18),
      bodyMedium: base.textTheme.bodyMedium?.copyWith(fontSize: 17),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(minimumSize: buttonSize, textStyle: buttonText, shape: shape),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(minimumSize: buttonSize, textStyle: buttonText, shape: shape),
    ),
    inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
  );
}

class QuizApp extends StatelessWidget {
  const QuizApp({super.key, required this.api, required this.auth});

  final QuizApi api;
  final AuthController auth;

  @override
  Widget build(BuildContext context) {
    return AppScope(
      api: api,
      auth: auth,
      child: MaterialApp(
        title: 'Schoolmela Quiz',
        theme: _theme(Brightness.light),
        darkTheme: _theme(Brightness.dark),
        home: ListenableBuilder(
          listenable: auth,
          builder: (context, _) {
            if (!auth.restored) return const Scaffold(body: Center(child: CircularProgressIndicator()));
            final user = auth.user;
            if (user == null) return const LoginScreen();
            if (!user.isStudent) return const _TeachersUseTheWebsite();
            // Keyed by user so nothing from a previous student's session is kept.
            return HomeScreen(key: ValueKey(user.id));
          },
        ),
      ),
    );
  }
}

class _TeachersUseTheWebsite extends StatelessWidget {
  const _TeachersUseTheWebsite();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const MessageBox(
                'This app is for students. Teachers, please use the Schoolmela Quiz website.',
                error: false,
              ),
              const SizedBox(height: 20),
              BigButton(label: 'Log out', onPressed: () => AppScope.of(context).auth.logout()),
            ],
          ),
        ),
      ),
    );
  }
}
