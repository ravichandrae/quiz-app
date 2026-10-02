import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app.dart';
import '../widgets/common.dart';
import 'register_screen.dart';

/// Keeps only digits, so "98765 43210" and "98765-43210" both work.
String digitsOnly(String value) => value.replaceAll(RegExp(r'\D'), '');

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _mobile = TextEditingController();
  final _pin = TextEditingController();
  String? _error;
  Map<String, String> _fieldErrors = const {};
  bool _busy = false;

  @override
  void dispose() {
    _mobile.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _error = null;
      _fieldErrors = const {};
    });
    try {
      await AppScope.of(context).auth.login(digitsOnly(_mobile.text), _pin.text);
      // The app switches to the home screen by itself.
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _fieldErrors = e.fieldErrors;
        _error = e.fieldErrors.isEmpty ? e.message : null;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: AutofillGroup(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'Schoolmela Quiz',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          color: Theme.of(context).colorScheme.primary,
                          fontWeight: FontWeight.w800,
                        ),
                  ),
                  const SizedBox(height: 24),
                  Text('Log in', style: Theme.of(context).textTheme.headlineSmall),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _mobile,
                    keyboardType: TextInputType.phone,
                    autofillHints: const [AutofillHints.telephoneNumber],
                    textInputAction: TextInputAction.next,
                    maxLength: 14,
                    decoration: InputDecoration(
                      labelText: 'Mobile number',
                      errorText: _fieldErrors['mobile'],
                      counterText: '',
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _pin,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    autofillHints: const [AutofillHints.password],
                    maxLength: 6,
                    onSubmitted: (_) => _busy ? null : _submit(),
                    decoration: InputDecoration(labelText: 'PIN', errorText: _fieldErrors['pin'], counterText: ''),
                  ),
                  if (_error != null) ...[const SizedBox(height: 12), MessageBox(_error!)],
                  const SizedBox(height: 20),
                  BigButton(label: _busy ? 'Please wait…' : 'Log in', onPressed: _busy ? null : _submit),
                  const SizedBox(height: 12),
                  TextButton(
                    onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const RegisterScreen())),
                    child: const Text('New here? Create an account'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
