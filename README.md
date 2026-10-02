# Schoolmela Quiz

Timed multiple-choice quizzes for rural school students (ages 12–15), with an admin console for teachers.
Requirements: [Schoolmela Quiz App Requirements.md](Schoolmela%20Quiz%20App%20Requirements.md).

## Layout

| Path       | What                                                        |
|------------|-------------------------------------------------------------|
| `backend/` | Spring Boot 4 API (Java 21), PostgreSQL, Flyway migrations   |
| `web/`     | React + TypeScript web app (Vite), served by nginx in Docker |
| `mobile/`  | Flutter Android app for students — see [mobile/README.md](mobile/README.md) |

## Run everything with Docker

Requires Docker Desktop (or Docker Engine with Compose v2).

```sh
docker compose up --build
```

| URL                                        | Service                         |
|--------------------------------------------|---------------------------------|
| http://localhost:3000                      | Web app                         |
| http://localhost:8080/swagger-ui.html      | API docs                        |
| http://localhost:8080/actuator/health      | Backend health                  |
| localhost:5432 (user/password/db `quiz`)   | PostgreSQL                      |

The web app reaches the API through `/api/*`, which nginx proxies to the backend.
Ports and credentials can be overridden by copying `.env.example` to `.env`.

### Logging in

- **Students** create their own account at http://localhost:3000/register (name, 10-digit mobile number, 4–6 digit PIN).
- **Admin**: Compose creates a first admin on startup — mobile `9999999999`, PIN `123456`.
  Admins can add more admins from the Students page. Change these defaults (`BOOTSTRAP_ADMIN_*`) and
  `JWT_SECRET` in `.env` for anything other than local development.

| Setting                     | Default (Compose)   | Purpose                                                  |
|-----------------------------|---------------------|----------------------------------------------------------|
| `JWT_SECRET`                | dev-only value      | Signs login tokens; at least 32 characters. **Required** |
| `BOOTSTRAP_ADMIN_MOBILE`    | `9999999999`        | First admin's mobile; skipped if already registered      |
| `BOOTSTRAP_ADMIN_PIN`       | `123456`            | First admin's PIN                                        |
| `BOOTSTRAP_ADMIN_NAME`      | `Admin`             | First admin's name                                       |

Login rules: 5 wrong PINs lock an account for 15 minutes; locked students show as "Locked" on the
Students page. If a student forgets their PIN, an admin uses **Reset PIN** to set a new one with them,
which also removes the lock and logs the student out on other devices. Login tokens last 15 minutes and
are renewed automatically for up to 7 days; the
web app forgets the login when the browser tab is closed, since students often share a tablet.

## Develop locally

Start only the database:

```sh
docker compose up -d db
```

**Backend** (needs JDK 21):

```sh
cd backend
JWT_SECRET=local-dev-secret-change-me-0123456789abcdef \
BOOTSTRAP_ADMIN_MOBILE=9999999999 BOOTSTRAP_ADMIN_PIN=123456 \
./mvnw spring-boot:run     # http://localhost:8080
./mvnw verify              # tests; needs Docker running (Testcontainers)
```

If you only have an older JDK, run the tests in a container instead:

```sh
docker run --rm -v "$PWD/backend:/app" -v quiz-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app maven:3.9-eclipse-temurin-21 mvn -B verify
```

**Web** (needs Node 22):

```sh
cd web
npm install
npm run dev                # http://localhost:5173, proxies /api to localhost:8080
npm test
npm run lint
```
