# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Schoolmela Quiz: timed multiple-choice quizzes for rural school students (ages 12–15) plus an admin console for teachers. Product requirements live in `Schoolmela Quiz App Requirements.md`; user-facing docs, deployment, and security notes are in `docs/`.

Three clients of one API:

- `backend/`: Spring Boot 4 (Java 21), PostgreSQL 17, Flyway, JPA
- `web/`: React 19 + TypeScript (Vite), served by nginx in Docker. Used by both students and admins.
- `mobile/`: Flutter Android app, **students only** (a teacher who logs in is told to use the website)
- `deploy/`: Helm chart (`deploy/helm/schoolmela-quiz`), Postgres backup script, k6 load test (`deploy/loadtest/quiz-day.js`)

## Commands

Full stack: `docker compose up --build` (web on :3000, API on :8080, Swagger at `/swagger-ui.html`). The dev admin is mobile `9999999999`, PIN `123456`.

Backend (from `backend/`; needs Docker running because tests use Testcontainers):

```sh
docker compose up -d db                       # from repo root, DB only for local dev
JWT_SECRET=local-dev-secret-change-me-0123456789abcdef \
BOOTSTRAP_ADMIN_MOBILE=9999999999 BOOTSTRAP_ADMIN_PIN=123456 ./mvnw spring-boot:run
./mvnw verify                                 # all tests (what CI runs)
./mvnw test -Dtest=AttemptIntegrationTest     # one class
./mvnw test -Dtest='AttemptIntegrationTest#someMethod'
```

Web (from `web/`, Node 22):

```sh
npm run dev          # :5173, proxies /api -> localhost:8080 (strips the /api prefix)
npm test             # vitest run
npx vitest run src/pages/LoginPage.test.tsx   # one file
npm run lint         # oxlint
npm run build        # tsc -b && vite build
```

Mobile (from `mobile/`): `flutter analyze`, `flutter test`, `flutter run` (emulator reaches `http://10.0.2.2:3000/api`; override with `--dart-define=API_BASE_URL=...`). Release builds only allow HTTPS.

CI (`.github/workflows/ci.yml`) runs backend `mvnw verify`, web `npm audit`/lint/test/build, mobile analyze/test, `helm lint` + kubeconform, and `docker compose build`. Pushing a `v*` tag publishes images to GHCR (`release.yml`).

## Backend architecture

Package-by-feature under `org.schoolmela.quiz`: `auth`, `user`, `question`, `quiz`, `group`, `assignment`, `attempt`, plus `config` and `common`. Each feature has Controller → Service → Repository, with request/response records grouped in a `*Dtos` class.

- **URL scheme drives authorization** (`config/SecurityConfig`): `/admin/**` requires ADMIN, `/me/quizzes|attempts|results/**` requires STUDENT, `/auth/*` is public. Put new endpoints under the right prefix rather than adding method-level checks. There is no `/api` prefix on the backend; nginx and the Vite dev server strip it.
- **Auth**: stateless HS256 JWT access tokens (15 min) plus rotating refresh tokens stored in the DB (7 days). `ActiveUserJwtConverter` reloads the user on every request, so the role comes from the DB, not the token, and deactivated accounts stop working immediately. Login is mobile number + 4–6 digit PIN (bcrypt), with per-account lockout after 5 wrong PINs and a per-IP rate limit on `/auth/*` (`AuthRateLimitFilter`, `common/RateLimiter`).
- **Errors**: throw `common/ApiException` (status, code, message). `ApiExceptionHandler` renders RFC 9457 problem details with `code`, `detail`, and optional `errors` (field → message). The `detail` text is shown directly to students and teachers, so write it in plain, simple language. Use `ApiException.invalidField(...)` for business-rule validation so it looks like bean validation.
- **Quiz timing is server-side** (`attempt/Attempt`): starting an attempt snapshots the quiz (title, limits, questions as `AttemptQuestion`s), so later edits to the quiz don't affect attempts in progress. Each question's timer starts when it is served. `catchUp(now, grace)` lazily times out expired questions and finishes the attempt, and it is called on reads as well as writes because students may close the browser mid-quiz. `app.quiz.answer-grace` (3s) allows for slow networks.
- **Time**: always inject `java.time.Clock` and never call `Instant.now()` directly. Tests swap in `MutableClock` (integration) or `SettableClock` (unit) to move time forward.
- **Schema**: Flyway migrations in `src/main/resources/db/migration` (`V<n>__name.sql`), with `ddl-auto: validate`. Add a new migration and never edit an existing one.
- Config lives under `app.*` in `application.yml`, bound to `@ConfigurationProperties` records in `config/`. `JWT_SECRET` (≥32 chars) is required. CSV exports use `app.reports.time-zone` (Asia/Kolkata).

### Backend tests

Integration tests extend `IntegrationTest`, which provides one shared Spring context and one Postgres container for all test classes. Because the database is shared and never reset between tests, use `uniqueMobile()` for any new user and don't assume tables are empty. Helpers: `register`, `login`, `accessTokenFor`, `asAdmin(request, json)`, `idOf`. The clock is reset after each test. The rate limit is effectively disabled there and has its own unit tests.

## Web architecture

- `src/api/client.ts` is the only place that talks to `fetch`. It stores the session in **sessionStorage** on purpose (students share tablets, so closing the tab logs out), refreshes an expired access token once on 401 (concurrent callers share a single refresh because refresh tokens rotate), and converts problem details to `ApiError`, whose `message` can be shown as-is. `apiDownload` handles authenticated file downloads such as CSV.
- One module per API area in `src/api/`. Pages are in `src/pages/`, and role gating uses `auth/RequireRole`.
- Tests are vitest + Testing Library (jsdom). Shared helpers are in `src/test-utils.tsx`, and tests are colocated as `*.test.tsx`.
- `web/nginx.conf.template` proxies `/api/*` to `API_UPSTREAM` and sets `X-Forwarded-For` (trusted only from `TRUSTED_PROXY_CIDR`). The backend uses that header for per-IP rate limiting.

## Mobile architecture

`lib/api/` (HTTP client and models, same API and error format as the web app), `lib/auth/` (session kept in Android encrypted storage until logout), `lib/screens/`. Widget tests use a fake API and a fake clock (`test/support.dart`). The integration test in `integration_test/` needs a real server and a student with an untaken quiz (see `mobile/README.md`).
