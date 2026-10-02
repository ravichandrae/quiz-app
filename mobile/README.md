# Schoolmela Quiz — Android app

Flutter app for **students**: log in or register, take assigned quizzes with the server-timed
countdown, see scores and review answers. Teacher tools are on the website only; a teacher who
logs in here is told to use the website.

It uses the same API as the web app. The login is kept in Android's encrypted storage until the
student taps **Log out**.

## Run against the local stack

Start the backend (`docker compose up` in the repository root), then:

```sh
cd mobile
flutter run                      # emulator: reaches http://10.0.2.2:3000/api by default
```

On a real phone on the same Wi-Fi, point it at your computer:

```sh
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:3000/api
```

Debug builds may use plain HTTP; **release builds only allow HTTPS**.

## Tests

```sh
flutter analyze
flutter test                     # widget tests with a fake API (timers use a fake clock)
```

End to end on an emulator or phone against a real server, with screenshots saved to
`build/screenshots`. It needs a student with an assigned quiz they have not taken yet
(each run uses up the attempt):

```sh
flutter drive -d emulator-5554 \
  --driver=test_driver/integration_test.dart --target=integration_test/app_test.dart \
  --dart-define=TEST_MOBILE=9123456781 --dart-define=TEST_PIN=2468 \
  "--dart-define=TEST_QUIZ=Emulator Test Quiz"
```

## Release build

```sh
flutter build apk --release --split-per-abi --dart-define=API_BASE_URL=https://quiz.example.org/api
```

`app-armeabi-v7a-release.apk` (about 14 MB) suits most low-cost phones; `arm64-v8a` newer ones.
Before publishing, set up a release signing key in `android/app/build.gradle.kts` (it currently
signs with the debug key).
