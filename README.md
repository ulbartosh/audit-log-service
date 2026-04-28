# Audit Log Service

Internal append-only audit-log service that ingests events from other company services and stores them immutably. Backs compliance, security, and observability use cases.

## Tech stack

- Java 21
- Spring Boot 3
- Gradle (Kotlin DSL)
- PostgreSQL 16 via Flyway migrations
- JUnit 5 + Mockito for unit tests
- Testcontainers for integration tests
- ArchUnit for boundary tests
- JaCoCo for coverage (≥ 90% line gate)

## Quickstart

Requires Docker.

```bash
docker compose up -d
```

Once `app` is healthy:

| | URL |
| --- | --- |
| API | http://localhost:8080/audit-events |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Postgres (host) | `localhost:55432` (`auditlog` / `auditlog` / `auditlog`) |

Stop with `docker compose down` (add `-v` to drop the DB volume).

The compose stack publishes Postgres on host port `55432` rather than the standard `5432` so it can run alongside a local Postgres install. The `app` container talks to `db:5432` over the compose network and is unaffected.

## API

### `POST /audit-events`

Ingest a single event.

```json
{
  "actor": "alice",
  "action": "user.login",
  "resource": "project:42",
  "outcome": "SUCCESS",
  "context": { "ip": "10.0.0.1" }
}
```

- **Required:** `actor`, `action`.
- **Optional:** `resource`, `outcome` (defaults to `SUCCESS`), `context` (arbitrary JSON object).
- **Server-set:** `id` (UUID) and `occurredAt` (ISO-8601 instant). Any client-supplied `id` / `occurredAt` / `timestamp` is silently ignored.

Responses:

- `201 Created` — body is the persisted event; `Location: /audit-events/{id}`.
- `400 Bad Request` — `{"errors":[{"field":"...","message":"..."}]}`.

### `GET /audit-events`

Search events. All filters optional; results sorted by `occurredAt DESC`.

| Param | Type | Default | Notes |
| --- | --- | --- | --- |
| `actor` | string | — | Exact match |
| `resource` | string | — | Exact match |
| `from` | ISO-8601 instant | — | Inclusive lower bound on `occurredAt` |
| `to` | ISO-8601 instant | — | Inclusive upper bound on `occurredAt` |
| `page` | int | 0 | Zero-indexed |
| `size` | int | 50 | Capped at 500 |

Response: `200 OK` with

```json
{
  "items": [{ "...event..." }],
  "page": 0,
  "size": 50,
  "total": 137
}
```

### Event schema

| Field | Type | Source | Required |
| --- | --- | --- | --- |
| `id` | UUID | server | yes |
| `occurredAt` | ISO-8601 instant | server | yes |
| `actor` | string | client | yes |
| `action` | string | client | yes |
| `resource` | string | client | no |
| `outcome` | enum (`SUCCESS` / `DENIED` / `ERROR`) | client | defaults to `SUCCESS` |
| `context` | arbitrary JSON | client | no |

### Invariants

- **Append-only.** No `UPDATE` or `DELETE` is exposed by the API; the database enforces a `DO INSTEAD NOTHING` rule on `UPDATE`. Regression-tested by `AuditEventImmutabilityIT`.
- **Server-set timestamp.** Any client-supplied `occurredAt` / `timestamp` is silently dropped on POST.
- **Error response shape.** Every error body has the form `{"errors":[{"field"?,"message"}]}`. Field-level validation errors include `field`; other failures emit a single `{"message"}` object.

### Curl smoke

```bash
# Create
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"actor":"alice","action":"user.login","outcome":"SUCCESS"}'

# Search
curl 'http://localhost:8080/audit-events?actor=alice'

# Validation error (missing actor)
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"action":"user.login","outcome":"SUCCESS"}'
```

## Local development

### Run from sources

Requires JDK 21. Bring up just the database via compose, then run the app locally:

```bash
docker compose up -d db
AUDITLOG_DATASOURCE_URL=jdbc:postgresql://localhost:55432/auditlog \
./gradlew bootRun
```

Datasource defaults (when env vars are unset) are `jdbc:postgresql://localhost:5432/auditlog` / `auditlog` / `auditlog`. Override `AUDITLOG_DATASOURCE_{URL,USERNAME,PASSWORD}` to point at any other Postgres.

### Tests and gate

```bash
./gradlew test              # unit + ArchUnit, no Docker
./gradlew integrationTest   # Testcontainers Postgres, requires Docker
./gradlew build             # full gate: compile + test + integrationTest + spotlessCheck + jacoco verify (≥ 90% line)
./gradlew spotlessApply     # auto-fix formatting before re-running check
```

Reports:

- Unit / IT HTML — `build/reports/tests/{test,integrationTest}/index.html`
- Coverage HTML — `build/reports/jacoco/test/index.html`

### Git hooks (one-time per clone)

The repo ships pre-commit and pre-push hooks under `.githooks/`. Activate them once:

```bash
git config core.hooksPath .githooks
```

- `pre-commit` runs `./gradlew test spotlessCheck` (fast, no Docker).
- `pre-push` runs `./gradlew build` (full gate, requires Docker).

CI mirrors the pre-push gate, so a failing pre-push run almost always means a failing PR check.

## Further reading

- [`agents.md`](agents.md) — architecture, layered boundaries, build-health and PR invariants, working agreements for agents.
- [`PLAN.md`](PLAN.md) — phased implementation plan with per-step result notes and a PR review resolution log.
