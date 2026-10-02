import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app.dart';
import '../widgets/common.dart';
import 'login_screen.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _name = TextEditingController();
  final _mobile = TextEditingController();
  final _pin = TextEditingController();
  final _confirmPin = TextEditingController();
  final _school = TextEditingController();
  final _email = TextEditingController();
  String? _error;
  Map<String, String> _fieldErrors = const {};
  bool _busy = false;

  @override
  void dispose() {
    for (final c in [_name, _mobile, _pin, _confirmPin, _school, _email]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (_pin.text != _confirmPin.text) {
      setState(() => _fieldErrors = {'confirmPin': 'The two PINs are not the same'});
      return;
    }
    setState(() {
      _busy = true;
      _fieldErrors = const {};
    });
    final navigator = Navigator.of(context);
    try {
      await AppScope.of(context).auth.register(
            name: _name.text,
            mobile: digitsOnly(_mobile.text),
            pin: _pin.text,
            school: _school.text.trim(),
            email: _email.text.trim(),
          );
      // Logged in: go back to the start, where the home screen now shows.
      navigator.popUntil((route) => route.isFirst);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _fieldErrors = e.fieldErrors;
        _error = e.fieldErrors.isEmpty ? e.message : 'Please check the details you entered.';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _pinField(TextEditingController controller, String label, String errorKey, {String? helper}) {
    return TextField(
      controller: controller,
      obscureText: true,
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      maxLength: 6,
      decoration: InputDecoration(
        labelText: label,
        helperText: helper,
        errorText: _fieldErrors[errorKey],
        counterText: '',
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const gap = SizedBox(height: 14);
    return Scaffold(
      appBar: AppBar(title: const Text('Create an account')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _name,
                textCapitalization: TextCapitalization.words,
                maxLength: 100,
                decoration: InputDecoration(labelText: 'Your name', errorText: _fieldErrors['name'], counterText: ''),
              ),
              gap,
              TextField(
                controller: _mobile,
                keyboardType: TextInputType.phone,
                maxLength: 14,
                decoration: InputDecoration(labelText: 'Mobile number', errorText: _fieldErrors['mobile'], counterText: ''),
              ),
              gap,
              _pinField(_pin, 'Choose a PIN', 'pin', helper: '4 to 6 numbers. Remember it!'),
              gap,
              _pinField(_confirmPin, 'Type your PIN again', 'confirmPin'),
              gap,
              TextField(
                controller: _school,
                textCapitalization: TextCapitalization.words,
                maxLength: 150,
                decoration: InputDecoration(
                  labelText: 'School name (optional)',
                  errorText: _fieldErrors['school'],
                  counterText: '',
                ),
              ),
              gap,
              TextField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                maxLength: 254,
                decoration: InputDecoration(labelText: 'Email (optional)', errorText: _fieldErrors['email'], counterText: ''),
              ),
              if (_error != null) ...[gap, MessageBox(_error!)],
              const SizedBox(height: 20),
              BigButton(label: _busy ? 'Please wait…' : 'Create account', onPressed: _busy ? null : _submit),
            ],
          ),
        ),
      ),
    );
  }
}
