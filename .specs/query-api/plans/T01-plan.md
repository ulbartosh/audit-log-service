# T01 — V4 migration: new columns and recomposed indexes

## Context

T01 is the first of seven tasks in the [`query-api`](../) spec. It is a SQL-only Flyway step that prepares the schema for the rest of the work: structured `actor`/`resource` (T02), keyset pagination (T04–T07). Without T01 landed, the entity changes in T02 cannot map to the new columns, and the index recreation needed for efficient `(occurred_at DESC, id DESC)` keyset walks is absent.

Scope: add three columns to `audit_events` and `audit_events_archive`, drop and recreate the three filterable indexes on `audit_events` with an `id DESC` tiebreaker. No Java production-code changes. One IT extension to make the DoD verifiable.

References:
- Task definition: [`../tasks.md` § 01](../tasks.md#01--v4-migration-new-columns-and-recomposed-indexes)
- Design: [`../design.md` § Data model and migrations](../design.md#data-model-and-migrations)
- Requirements: enables `compliance/actor-structured`, `compliance/resource-structured`, `sre/payload-present` ACs (verified by later tasks).

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/resources/db/migration/V4__add_actor_type_resource_type_payload.sql` | **Add.** New Flyway migration. | Schema changes go through Flyway only (`AGENTS.md` § Architecture / Persistence rules). |
| `src/integrationTest/java/com/training/bartosh/auditlog/persistence/FlywayMigrationIT.java` | **Extend.** Add column-existence assertions for both tables and index-shape assertions for `audit_events`. | DoD requires verifying the migration's *shape*, not just that it ran. Existing IT only checks index names. |

Nothing else changes. README has no migration list to update; `build.gradle.kts` JaCoCo config already excludes non-Java artifacts.

## V4 SQL — exact body

Follows the V1–V3 style: uppercase keywords, aligned column names, no `IF NOT EXISTS`, single trailing newline.

```sql
ALTER TABLE audit_events
    ADD COLUMN actor_type    TEXT  NOT NULL DEFAULT 'USER',
    ADD COLUMN resource_type TEXT,
    ADD COLUMN payload       JSONB;

ALTER TABLE audit_events_archive
    ADD COLUMN actor_type    TEXT  NOT NULL DEFAULT 'USER',
    ADD COLUMN resource_type TEXT,
    ADD COLUMN payload       JSONB;

DROP INDEX idx_audit_events_actor_time;
DROP INDEX idx_audit_events_resource_time;
DROP INDEX idx_audit_events_time;

CREATE INDEX idx_audit_events_actor_time
    ON audit_events (actor, occurred_at DESC, id DESC);

CREATE INDEX idx_audit_events_resource_time
    ON audit_events (resource, occurred_at DESC, id DESC);

CREATE INDEX idx_audit_events_time
    ON audit_events (occurred_at DESC, id DESC);
```

Body is taken verbatim from [`../design.md` § Data model and migrations](../design.md#data-model-and-migrations) — no deviation.

Notes confirmed from exploration:
- Existing indexes are currently *two-column* shapes (`(actor, occurred_at DESC)`, etc.) — the `DROP INDEX` is required, not a no-op.
- `audit_events_archive` has no indexes today, so only columns are added there.
- Table is empty per design resolution #8 — no backfill, no row-touching statements.

## `FlywayMigrationIT` extension

Add **three** new `@Test` methods, alongside the existing `auditEventsTableExists` and `expectedIndexesExist`:

1. `v4AddsNewColumnsToAuditEvents` — query `information_schema.columns` for the three new columns on `audit_events`. Assert: each column exists; `actor_type` has `data_type = 'text'`, `is_nullable = 'NO'`, `column_default LIKE ''USER''::text` (Postgres normalizes the default); `resource_type` has `data_type = 'text'`, `is_nullable = 'YES'`; `payload` has `data_type = 'jsonb'`, `is_nullable = 'YES'`.

2. `v4AddsNewColumnsToAuditEventsArchive` — same three columns, same constraints, against `audit_events_archive`.

3. `v4RecreatesIndexesWithIdTiebreaker` — query `pg_indexes.indexdef` for the three indexes on `audit_events`. Assert each `indexdef` string contains:
   - `idx_audit_events_actor_time` → `(actor, occurred_at DESC, id DESC)` (textual contains check; Postgres canonicalizes the def so the assertion uses contains semantics).
   - `idx_audit_events_resource_time` → `(resource, occurred_at DESC, id DESC)`.
   - `idx_audit_events_time` → `(occurred_at DESC, id DESC)`.

The existing `expectedIndexesExist` test stays unchanged — it still passes because index names are preserved.

Style to match: low-level JDBC, raw `Connection` / `PreparedStatement` / `ResultSet`, no extra dependencies. The class already does this.

## Definition of Done

Mirrors `tasks.md` T01 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew integrationTest --tests "*FlywayMigrationIT*"` passes — including the three new `@Test` methods.
- [ ] Flyway applies V1 → V4 cleanly from an empty Postgres (`@ServiceConnection` Testcontainer brings up a fresh DB each run; passing IT is the evidence).
- [ ] New columns exist on both tables with correct types/defaults/nullability — asserted by `v4AddsNewColumnsToAuditEvents` and `v4AddsNewColumnsToAuditEventsArchive`.
- [ ] Three indexes on `audit_events` have shape `(filter, occurred_at DESC, id DESC)` — asserted by `v4RecreatesIndexesWithIdTiebreaker`.
- [ ] Existing tests untouched: `auditEventsTableExists` and `expectedIndexesExist` still pass; no other test changes.
- [ ] No new compiler warnings; spotless clean; no `TODO` without an issue reference; no `System.out.println` (`AGENTS.md` § Build health #3, #7).

## Verification — end-to-end manual

After local commit, before pushing the PR branch:

```bash
./gradlew clean build
./gradlew integrationTest --tests "*FlywayMigrationIT*" --info
```

Expected: both commands green. The `--info` run prints the Testcontainers Postgres connection log; tail it to confirm Flyway logs `Migrating schema "public" to version "4 - add actor type resource type payload"` and `Successfully applied 4 migrations`.

For a sanity check on the index shape (optional, ad-hoc):

```bash
# against a local Postgres after running migrations, or via the still-running Testcontainer:
psql -c "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'audit_events' ORDER BY indexname;"
```

Expected output contains three rows whose `indexdef` ends with `... DESC, id DESC)` for the two filter indexes and `(occurred_at DESC, id DESC)` for the time index.

## Out of scope (deferred to later tasks)

- Java entity field additions (`actorType`, `resourceType`, `payload`) — T02.
- Mapper updates to read/write the new columns — T02.
- New domain types (`Actor`, `Resource`, `Cursor`, `KeysetPage`) — T02 / T03.
- Application reading the new index shapes via cursor queries — T07.

The new columns are unread/unwritten by application code after T01 lands. This is intentional and verified by the existing test suite continuing to pass.

## Open questions

None. The DDL is taken verbatim from `design.md`; the IT location is chosen (extend `FlywayMigrationIT` in place, per user answer during planning).

## Branch & PR

- **Branch:** `query-api/t01-v4-migration` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3).
- **PR title:** `feat(query-api): V4 migration — actor_type, resource_type, payload columns + composite indexes`
- **PR description maps DoD to evidence:** new IT methods named, `./gradlew build` green link, quote of `pg_indexes` output if helpful.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 01 in `tasks.md` (per the `_(append after merge)_` placeholder).
