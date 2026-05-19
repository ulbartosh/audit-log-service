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

**Breaking change:** `actor` and `resource` are structured objects on both POST and GET.
Existing flat-string callers must update.

```json
{
  "actor": { "id": "alice", "type": "USER" },
  "action": "user.login",
  "resource": { "id": "project:42", "type": "project" },
  "outcome": "SUCCESS",
  "context": { "ip": "10.0.0.1" },
  "payload": { "amount": 100 }
}
```

- **Required:** `actor.id`, `action`.
- **Optional:** `actor.type` (defaults to `USER`), `resource`, `resource.type`, `outcome`
  (defaults to `SUCCESS`), `context` (environmental metadata), `payload` (event body).
- **Server-set:** `id` (UUID) and `occurredAt` (ISO-8601 instant). Any client-supplied `id` / `occurredAt` / `timestamp` is silently ignored.

Responses:

- `201 Created` — body is the persisted event; `Location: /audit-events/{id}`.
- `400 Bad Request` — `{"errors":[{"field":"...","message":"..."}]}`.

### `GET /audit-events`

Search events. All filters optional; results sorted by `(occurredAt DESC, id DESC)`.

| Param | Type | Default | Notes |
| --- | --- | --- | --- |
| `actor` | comma-separated string | - | One to ten actor IDs in a single parameter. Entries are trimmed, de-duplicated, and matched exactly against `actor.id`. Empty entries return `400`. |
| `resource` | string | - | Exact match. Non-blank when present. |
| `from` | ISO-8601 instant | — | Inclusive lower bound on `occurredAt` |
| `to` | ISO-8601 instant | — | Inclusive upper bound on `occurredAt` |
| `pageToken` | string | - | Opaque continuation token from the previous response. Omit for the first page. |
| `size` | int | 50 | Must be >= 1; silently capped at 500. |

Response: `200 OK` with

```json
{
  "items": [
    {
      "id": "2e71604d-0f51-4b7e-a32f-8d6f0d79068f",
      "occurredAt": "2026-05-12T12:00:00Z",
      "actor": { "id": "alice", "type": "USER" },
      "action": "user.login",
      "resource": { "id": "project:42", "type": "project" },
      "outcome": "SUCCESS"
    }
  ],
  "nextPageToken": "eyJ2IjoxLCJvY2N1cnJlZEF0IjoiMjAyNi0wNS0xMlQxMjowMDowMFoiLCJpZCI6IjJlNzE2MDRkLTBmNTEtNGI3ZS1hMzJmLThkNmYwZDc5MDY4ZiJ9"
}
```

`nextPageToken` is omitted when the current page exhausts the result set. Repeat the same filters on each follow-up request; the token carries only the position in the sort order.

Actor filters match any listed actor ID (`actor=alice,bob` behaves like `actor.id IN ("alice","bob")`). The ten-entry cap is applied before de-duplicating repeated IDs. `actor=`, `actor=alice,,bob`, `actor=alice,`, and eleven or more raw actor entries return `400 Bad Request` with `field: "actor"`.

### Event schema

| Field | Type | Source | Required |
| --- | --- | --- | --- |
| `id` | UUID | server | yes |
| `occurredAt` | ISO-8601 instant | server | yes |
| `actor` | object `{ id, type }` | client | yes |
| `actor.id` | string | client | yes |
| `actor.type` | enum (`USER`) | client | defaults to `USER` |
| `action` | string | client | yes |
| `resource` | object `{ id, type }` | client | no |
| `resource.id` | string | client | yes, when `resource` is present |
| `resource.type` | string | client | no |
| `outcome` | enum (`SUCCESS` / `DENIED` / `ERROR`) | client | defaults to `SUCCESS` |
| `context` | arbitrary JSON | client | no |
| `payload` | arbitrary JSON | client | no |

### Invariants

- **Append-only API surface and storage.** No `UPDATE` or `DELETE` is exposed by the API, and the database blocks both operations on `audit_events` with `DO INSTEAD NOTHING` rules. Retention copies expired rows into `audit_events_archive` but leaves the source rows intact. Regression-tested by `AuditEventImmutabilityIT`.
- **Server-set timestamp.** Any client-supplied `occurredAt` / `timestamp` is silently dropped on POST.
- **Error response shape.** Every error body has the form `{"errors":[{"field"?,"message"}]}`. Field-level validation errors include `field`; other failures emit a single `{"message"}` object.

### Curl smoke

```bash
# Create
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"actor":{"id":"alice"},"action":"user.login","outcome":"SUCCESS","payload":{"source":"curl"}}'

# Search for one or more actors
curl 'http://localhost:8080/audit-events?actor=alice,bob'

# Continue from a previous search response
curl 'http://localhost:8080/audit-events?actor=alice,bob&size=25&pageToken=<nextPageToken>'

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

### Retention

Old events are copied into `audit_events_archive` once they pass the retention threshold. The source rows remain in `audit_events`, so archival is additive and fully append-only. Defaults:

| Property | Default | Notes |
| --- | --- | --- |
| `auditlog.retention.days` | `365` | Events older than this are copied into the archive table |
| `auditlog.retention.cron` | `0 0 3 * * *` | Spring 6-field cron: `second minute hour day-of-month month day-of-week` |
| `auditlog.retention.zone` | `UTC` | Time zone used by the scheduler |

The retention job is internal-only, idempotent, and keeps the REST API plus the primary audit table append-only.

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
