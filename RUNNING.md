# Running the Audit Log Service

This guide covers two run modes, the Swagger UI walkthrough, and how to run the test suites locally. For architecture and invariants, see `agents.md`. For implementation status, see `PLAN.md`.

## Prerequisites

- **Docker Desktop** (required for `docker compose` mode and for the integration tests).
- **Java 21** (only required for the Gradle / `bootRun` mode, not for `docker compose`). The repo's `gradle.properties` pins Gradle's toolchain to Homebrew `openjdk@21`; if you use a different JDK install, override with `org.gradle.java.installations.paths` or remove the pin.

Verify Docker is running before continuing:

```bash
docker ps
```

If you see "Cannot connect to the Docker daemon", start Docker Desktop first (`open -a Docker` on macOS) and re-check.

## Mode 1 — `docker compose` (no local Java/Gradle needed)

Brings up Postgres and the app together. The app image is built from the `Dockerfile` (multi-stage: JDK 21 builder → JRE 21 runtime).

```bash
# Build the image and start both services in the background.
docker compose up -d --build

# Tail logs (Ctrl+C to detach — services keep running).
docker compose logs -f app

# Stop the stack but keep the database volume.
docker compose down

# Stop and wipe the database volume (fresh DB on next start).
docker compose down -v
```

Endpoints once `app` is healthy:

- API: `http://localhost:8080/audit-events`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (302 → `/swagger-ui/index.html`)
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Postgres on the host: `localhost:55432` (mapped to the container's 5432; the host port is shifted to avoid colliding with a local Postgres install — change in `docker-compose.yml` if you want the standard port).

Connection settings the `app` service uses (override via `docker-compose.yml` if you need different credentials):

| Var | Value |
| --- | --- |
| `AUDITLOG_DATASOURCE_URL` | `jdbc:postgresql://db:5432/auditlog` |
| `AUDITLOG_DATASOURCE_USERNAME` | `auditlog` |
| `AUDITLOG_DATASOURCE_PASSWORD` | `auditlog` |

The first build takes a few minutes (downloads Gradle distribution + dependencies inside the builder stage). Subsequent builds are faster thanks to Docker's layer cache.

## Mode 2 — `./gradlew bootRun` (local JVM)

Runs the app directly from sources. Requires a Postgres reachable from the host. The simplest path is to bring up just the `db` service from compose; remember the host-side port is `55432`:

```bash
docker compose up -d db
AUDITLOG_DATASOURCE_URL=jdbc:postgresql://localhost:55432/auditlog \
./gradlew bootRun
```

Or point at any other Postgres via env vars:

```bash
AUDITLOG_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditlog \
AUDITLOG_DATASOURCE_USERNAME=auditlog \
AUDITLOG_DATASOURCE_PASSWORD=auditlog \
./gradlew bootRun
```

Defaults (when env vars are unset) are `jdbc:postgresql://localhost:5432/auditlog` / `auditlog` / `auditlog` — convenient for a Postgres installed directly on the host. When using the compose `db` service from `bootRun`, override `AUDITLOG_DATASOURCE_URL` to use port `55432` as shown above.

Stop with `Ctrl+C`. The DB keeps running until `docker compose down`.

## Smoke test (curl)

Same checklist for either run mode:

```bash
# Create an event → 201 Created with Location header.
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"actor":"u1","action":"user.login","outcome":"SUCCESS"}'

# Search by actor.
curl 'http://localhost:8080/audit-events?actor=u1'

# Validation error → 400 with field errors.
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"action":"user.login","outcome":"SUCCESS"}'
```

## Using Swagger UI

Open `http://localhost:8080/swagger-ui.html` in a browser. The page auto-loads the OpenAPI spec from `/v3/api-docs` and renders both endpoints under the `audit-event-controller` group.

To exercise the API from the page:

1. Expand `POST /audit-events` → click **Try it out**.
2. Replace the request body with, for example:
   ```json
   {
     "actor": "alice",
     "action": "user.login",
     "resource": "project:42",
     "outcome": "SUCCESS",
     "context": { "ip": "10.0.0.1" }
   }
   ```
3. Click **Execute**. The response panel shows `201`, the `Location` header, and the persisted event (note the server-set `id` and `occurredAt`).
4. Expand `GET /audit-events` → **Try it out** → set `actor=alice` → **Execute**. The response shows the event you just created inside `items`.
5. Try a validation error: POST a body without `actor` and confirm the `400` body has an `errors` array with the failed field.

The raw spec is downloadable via the link at the top of the page or directly:

```bash
curl http://localhost:8080/v3/api-docs | jq .
```

## Enabling git hooks (one-time per clone)

The repo ships pre-commit and pre-push hooks under `.githooks/`. They aren't active until you point git at that folder:

```bash
git config core.hooksPath .githooks
```

What runs:

- **pre-commit** — `./gradlew test spotlessCheck`. Fast, no Docker needed. Catches unit-test regressions and formatting drift before a commit lands.
- **pre-push** — `./gradlew build`. Full gate including integration tests (Testcontainers, **needs Docker running**). If you're pushing without Docker available, run `git push --no-verify` deliberately and rely on CI.

CI mirrors the pre-push gate via `.github/workflows/build.yml`, so a failing pre-push run almost always means a failing PR check.

## Running the tests

Independent of run mode — see `PLAN.md` "Verification" section for the full gate.

```bash
./gradlew test              # unit tests, no Docker
./gradlew integrationTest   # Testcontainers (requires Docker)
./gradlew build             # full gate: compile + test + integrationTest + spotlessCheck
./gradlew spotlessApply     # fix formatting before re-running check
```

Reports land in `build/reports/tests/test/index.html` and `build/reports/tests/integrationTest/index.html`.

## Troubleshooting

- **`docker compose up` hangs on "waiting for db"** — the healthcheck retries for ~60 s. Inspect with `docker compose logs db`. A common cause is a stale volume from an earlier failed run; wipe with `docker compose down -v`.
- **App container exits with `Connection refused`** — `db` healthcheck passed but Postgres is rejecting the connection. Confirm credentials in `docker-compose.yml` match the `POSTGRES_*` vars.
- **Port 8080 / 5432 already in use** — change the host-side mapping in `docker-compose.yml` (`"8090:8080"`, etc.).
- **Swagger UI 404** — confirm `springdoc.swagger-ui.path` in `application.yml` is `/swagger-ui.html`. The redirect target is `/swagger-ui/index.html`.
- **Integration tests fail with "Could not find a valid Docker environment"** — Docker Desktop isn't running or the socket isn't reachable. Start Docker, run `docker ps`, then re-run.
