# Query API — Tasks

Execution plan for the work specified in [`requirements.md`](./requirements.md) and designed in [`design.md`](./design.md).

Each step is one safe commit / one PR (`AGENTS.md` § PR invariants #2). Branch name: `query-api/t<step-id>-<short-name>`. Every step builds green on its own (`AGENTS.md` § Build health #1). On execution, append a 1–3 line result to the bottom of each step (mirrors `PLAN.md` convention).

Acceptance criteria are referenced by `<persona>/<short-tag>`, where `<persona>` is one of `compliance`, `sre`, `analyst` and `<short-tag>` quotes the AC heading from [`requirements.md`](./requirements.md) § Acceptance criteria.

---

## Dependency graph

```
01 ──┬─► 02 ──────────────────────────────────────────────┐
     │                                                    ▼
     └─► 03 ──┬─► 04 ─────────────────────────────────► 07 ─► 08
             ├─► 05 ─────────────────────────────────────►
             └─► 06 ─────────────────────────────────────►
```

- **01** is independent.
- **02** and **03** can run in parallel after 01.
- **04**, **05**, **06** can run in parallel after 03.
- **07** is the keyset wiring step; requires 02, 04, 05, 06 all landed.
- **08** is the actor-list filter step; requires 07.

---

## 01 — V4 migration: new columns and recomposed indexes

**Branch:** `query-api/t01-v4-migration`

**Refs**
- Design: [`design.md` § Data model and migrations](./design.md#data-model-and-migrations) — migration `V4__add_actor_type_resource_type_payload.sql`.
- Requirements: enables `compliance/actor-structured`, `compliance/resource-structured`, `sre/payload-present`.

**Scope**
- Add SQL migration `src/main/resources/db/migration/V4__add_actor_type_resource_type_payload.sql`.
- Columns added on both `audit_events` and `audit_events_archive`: `actor_type TEXT NOT NULL DEFAULT 'USER'`, `resource_type TEXT`, `payload JSONB`.
- Drop the three legacy indexes (`idx_audit_events_actor_time`, `…_resource_time`, `…_time`) and recreate as `(filter, occurred_at DESC, id DESC)` composites; the `_time` index becomes `(occurred_at DESC, id DESC)`.
- No Java changes in this step.

**Definition of Done**
- `./gradlew build` green.
- `FlywayMigrationIT` (or equivalent) applies V1 → V4 cleanly from an empty database (`AGENTS.md` § Build health #6).
- `\d audit_events` in a fresh Testcontainers Postgres shows the three new columns with the documented types/defaults/nullability.
- The three recreated indexes exist with the `(filter, occurred_at DESC, id DESC)` shape — assert via `pg_indexes` query in an IT, or in a dedicated `IndexShapeIT`.
- Existing application code still compiles and existing tests still pass (new columns are unread for now).

**Dependencies:** none.

**Execution result:**
_(append after merge)_

---

## 02 — Structured `actor` / `resource` and `payload` end-to-end

**Branch:** `query-api/t02-structured-shape`

**Refs**
- Design: [`design.md` § API contract → `GET /audit-events`](./design.md#api-contract), [§ Changes to existing `POST /audit-events`](./design.md#changes-to-existing-post-audit-events), [§ Layer integration](./design.md#layer-integration).
- Requirements ACs:
  - `compliance/actor-structured` — "shall return `actor` as a structured object `{ id, type }`".
  - `compliance/resource-structured` — "shall return `resource` as a structured object `{ id, type }`".
  - `sre/payload-present` — "when an event has a `payload`, … include the field".
  - `sre/payload-absent` — "when an event has no `payload`, … omit the field".
  - `sre/context-present`, `sre/context-absent` — re-asserted under new response shape.

**Scope**
- `domain/`: new `ActorType` enum (`USER` only), records `Actor(String id, ActorType type)` and `Resource(String id, String type)` with compact-ctor invariants from [`design.md` § Layer integration → domain/](./design.md#layer-integration). Update `AuditEvent` and `NewAuditEvent` to use `Actor` / `Resource` and add `JsonNode payload`.
- `persistence/`: `AuditEventEntity` + `AuditEventArchiveEntity` gain `actorType`, `resourceType`, `payload` fields (annotations per design). `AuditEventMapper` reads/writes the new fields per [`design.md` § Layer integration → persistence/](./design.md#layer-integration).
- `controller/dto/`: new `ActorRequest`, `ActorResponse`, `ResourceRequest`, `ResourceResponse`. `CreateAuditEventRequest` and `AuditEventResponse` swap to the new shapes; `payload` added to both with `@JsonInclude(NON_NULL)` on the response.
- `controller/AuditEventController.create(...)`: defaults `actor.type` to `USER` when omitted while mapping the request into the domain model; do not implement the default as a DTO field initializer.
- Service: `record(...)` propagates the new fields. `search(...)` continues to use offset paging in this step.

**Definition of Done**
- `./gradlew build` and `./gradlew check` green; JaCoCo ≥ 90% (`AGENTS.md` § Build health #9).
- Domain unit tests: `Actor` rejects null/blank `id` and null `type`; `Resource` rejects null/blank `id`, accepts null `type`, rejects blank `type` when non-null.
- POST integration test: posting `actor: { id, type }` + `resource: { id, type }` + `payload` round-trips through DB and is returned by GET with the same structured shape — covers `compliance/actor-structured`, `compliance/resource-structured`, `sre/payload-present`.
- POST integration test: `actor.type` omitted defaults to `USER` on read-back.
- POST integration test: `resource.type` present but blank returns `400`.
- GET integration test asserts the JSON keys `payload` and `context` are **absent** (not present-as-null) when the stored event has them null — covers `sre/payload-absent`, `sre/context-absent`.
- ArchUnit boundary tests still pass (no new `org.springframework.*` / `jakarta.persistence.*` imports in `domain/`).
- README updated if the POST/GET examples in it change (`AGENTS.md` § PR invariant #4).

**Dependencies:** 01.

**Execution result:**
_(append after merge)_

---

## 03 — Add `Cursor` and `KeysetPage<T>` domain types (unused)

**Branch:** `query-api/t03-cursor-keysetpage`

**Refs**
- Design: [`design.md` § Layer integration → domain/](./design.md#layer-integration), [§ Pagination strategy and cursor format](./design.md#pagination-strategy-and-cursor-format).
- Requirements ACs enabled: `analyst/pagination`, `analyst/cap-500`, `analyst/exactly-once-pagination`, `analyst/beyond-end`, `analyst/actor-order-pagination`.

**Scope**
- `domain/Cursor.java` — `record Cursor(Instant occurredAt, UUID id)`; compact ctor rejects null fields.
- `domain/KeysetPage.java` — `record KeysetPage<T>(List<T> items, Optional<Cursor> nextCursor)`; compact ctor rejects null `items` and null `nextCursor` (wrap as `Optional.empty()` when there's no next page).
- No callers — types compile but are unreferenced. Build stays green.

**Definition of Done**
- `./gradlew build` green.
- Unit tests for both compact ctors covering: null `occurredAt`, null `id`, null `items`, null `nextCursor`. Each assertion fails loudly if the invariant is removed.
- ArchUnit: both types live in `domain/` and have zero `org.springframework.*` / `jakarta.persistence.*` imports.

**Dependencies:** 01 (logical — same spec; no file-level dep on 01).

**Execution result:**
_(append after merge)_

---

## 04 — Add `afterCursor` specification helper (unused)

**Branch:** `query-api/t04-after-cursor-spec`

**Refs**
- Design: [`design.md` § Layer integration → persistence/ → `AuditEventSpecifications`](./design.md#layer-integration), [§ Pagination strategy → Next-page predicate](./design.md#pagination-strategy-and-cursor-format).
- Requirements ACs enabled: `analyst/exactly-once-pagination`, `analyst/beyond-end`, `analyst/actor-order-pagination`.

**Scope**
- Add `public static Specification<AuditEventEntity> afterCursor(Instant ts, UUID lastId)` to `AuditEventSpecifications` with the exact body from [`design.md` § Layer integration](./design.md#layer-integration) (uses `AuditEventEntity_.occurredAt` and `AuditEventEntity_.id` from the JPA Metamodel).
- No callers; the spec exists alongside `byActor` / `byResource` / `occurredAtOrAfter` / `occurredAtOrBefore`.

**Definition of Done**
- `./gradlew build` green.
- A `@DataJpaTest`-level integration test seeds rows with known `(occurredAt, id)` pairs and asserts that `afterCursor(ts, id)` returns exactly the rows strictly less than that cursor under `(occurredAt DESC, id DESC)` ordering — both the strict-`<` branch and the `=` + `<id` tiebreaker branch are exercised.

**Dependencies:** 03.

**Execution result:**
_(append after merge)_

---

## 05 — Add `PageTokenCodec` and `InvalidPageTokenException` (unused)

**Branch:** `query-api/t05-page-token-codec`

**Refs**
- Design: [`design.md` § Pagination strategy and cursor format → Cursor / Malformed token](./design.md#pagination-strategy-and-cursor-format), [§ Layer integration → controller/](./design.md#layer-integration).
- Requirements ACs enabled: `analyst/pagination`, `analyst/malformed-token`.

**Scope**
- `controller/PageTokenCodec.java` — bean that encodes a `Cursor` to base64-url JSON `{"v":1,"occurredAt":…,"id":…}` and decodes it back. Decoder rejects: non-base64, malformed JSON, missing fields, `v != 1`, non-ISO-8601 `occurredAt`, non-UUID `id` → throws `InvalidPageTokenException`.
- `controller/InvalidPageTokenException.java` — runtime exception carrying a `field` (`"pageToken"`) and a message.
- `controller/GlobalExceptionHandler` — map `InvalidPageTokenException` → `400 Bad Request` with the existing `{ errors: [{ field, message }] }` envelope.
- Base64 stays in `controller/` only (domain purity per [`design.md` § AGENTS.md alignment](./design.md#agentsmd-alignment)).

**Definition of Done**
- `./gradlew build` green.
- Unit tests: round-trip for a representative `Cursor`; encoder output is base64-url (no `+`/`/`/`=` padding chars depending on configured flavor — assert by attempting to decode); decoder rejects each malformed input class above with `InvalidPageTokenException`.
- Controller-level slice test (`@WebMvcTest`) confirms `InvalidPageTokenException` thrown anywhere in the request path renders as `400` with `errors[0].field == "pageToken"`.

**Dependencies:** 03.

**Execution result:**
_(append after merge)_

---

## 06 — Add `KeysetPageResponse<T>` DTO (unused)

**Branch:** `query-api/t06-keyset-page-response`

**Refs**
- Design: [`design.md` § API contract → `GET /audit-events` response](./design.md#api-contract), [§ Layer integration → controller/](./design.md#layer-integration).
- Requirements ACs enabled: `compliance/empty-result`, `analyst/beyond-end`.

**Scope**
- `controller/dto/KeysetPageResponse.java` — `record KeysetPageResponse<T>(List<T> items, String nextPageToken)`. Annotate with `@JsonInclude(NON_NULL)` so `nextPageToken` is omitted on serialization when null.
- No callers; coexists with `PagedResponse<T>`.

**Definition of Done**
- `./gradlew build` green.
- Unit test serializes a `KeysetPageResponse<Object>` instance with `nextPageToken == null` via the project's configured `ObjectMapper`; asserts the `"nextPageToken"` key is **absent** from the JSON output (not present-as-`null`).
- Unit test serializes a non-null `nextPageToken` and asserts it appears as a string field.

**Dependencies:** 03.

**Execution result:**
_(append after merge)_

---

## 07 — Wire keyset pagination through service + controller; drop `PagedResponse`

**Branch:** `query-api/t07-keyset-wire`

**Refs**
- Design: [`design.md` § API contract → `GET /audit-events`](./design.md#api-contract), [§ Pagination strategy and cursor format](./design.md#pagination-strategy-and-cursor-format), [§ Layer integration → service/, controller/](./design.md#layer-integration).
- Requirements ACs:
  - `compliance/resource-filter`, `compliance/from-filter`, `compliance/to-filter`, `compliance/and-filters` — re-asserted under keyset.
  - `compliance/empty-result` — "`200 OK` with `items: []` and `nextPageToken` omitted".
  - `compliance/from-malformed`, `compliance/to-malformed`, `compliance/from-after-to` — existing rejections preserved.
  - `sre/order-desc` — most-recent-first under `(occurredAt DESC, id DESC)`.
  - `analyst/pagination` — paginate result sets larger than one response.
  - `analyst/cap-500` — `size` silently capped at 500.
  - `analyst/exactly-once-pagination` — consecutive pages return each matching row exactly once.
  - `analyst/beyond-end` — page beyond end → `200` + `items: []` + no `nextPageToken`.
  - `analyst/malformed-token` — `400` with `errors[0].field == "pageToken"`.

**Scope**
- `service/SearchQuery` — replace `int page, int size` with `Optional<Cursor> cursor, int size`.
- `service/AuditEventService.search(...)` — return `KeysetPage<AuditEvent>`. Build `Specification` from filters; if `cursor` present, compose with `afterCursor`. Sort by `(occurred_at DESC, id DESC)`. Fetch `size + 1` rows with a limit-style query that does not execute a total count. If `result.size() > size`, drop the extra row and set `nextCursor = Optional.of(new Cursor(lastInRange.occurredAt(), lastInRange.id()))`; otherwise `Optional.empty()`.
- `controller/AuditEventController.search(...)` — replace `page` / `size` params with `Optional<String> pageToken` + `@RequestParam(defaultValue = "50") int size`. Decode/encode via `PageTokenCodec`. Reject `size < 1`; silently cap `size > 500`. Return `KeysetPageResponse<AuditEventResponse>`.
- `controller/dto/PagedResponse.java` — **delete**. If any other production caller still imports it, leave it and document why in the PR description (no other caller exists at the time this spec was written; verify with `rg` before deleting).
- Update README and any quickstart examples that show offset paging on `GET /audit-events`.

**Definition of Done**
- `./gradlew build` and `./gradlew check` green; JaCoCo ≥ 90%.
- Integration tests added alongside `AuditEventControllerIT` under `src/integrationTest/java/com/training/bartosh/auditlog/controller/`:
  - **`compliance/empty-result`** — GET with filters matching zero rows → `200`, body `items: []`, JSON has no `nextPageToken` key.
  - **`compliance/from-after-to`** — already covered for `400`; re-run under keyset to confirm shape preserved.
  - **GET validation** — blank `resource` returns `400`; `size < 1` and non-integer `size` return `400`.
  - **`sre/order-desc`** — seed N rows with mixed `occurredAt`/`id`; GET returns them strictly newest-first.
  - **`analyst/pagination`** — seed > `size` rows, walk pages via `nextPageToken`, assert union equals seeded set with no duplicates.
  - **`analyst/cap-500`** — request `size=10000` → response carries at most 500 items; no error.
  - **`analyst/exactly-once-pagination`** — page 1, insert new row, page 2: assert every originally-seeded matching id appears exactly once and the new row appears on neither page — verifies the backward-walk stability claim in [`design.md` § Pagination → Stability under concurrent ingest](./design.md#pagination-strategy-and-cursor-format).
  - **`analyst/beyond-end`** — drive the cursor to past the last row → `200`, `items: []`, no `nextPageToken`.
  - **`analyst/malformed-token`** — `pageToken=not-base64` and `pageToken=<valid base64 of {"v":2,…}>` both yield `400` with `errors[0].field == "pageToken"`.
- ArchUnit boundary tests still pass.
- No reference to `PagedResponse` remains in production code; if kept, the PR description states why.
- README example for `GET /audit-events` updated to show `pageToken` / `size` and `nextPageToken`.

**Dependencies:** 02, 04, 05, 06.

**Execution result:**
_(append after merge)_

---

## 08 — Comma-separated actor list filters

**Branch:** `query-api/t08-actor-list-filter`

**Refs**
- Design: [`design.md` § API contract → `GET /audit-events`](./design.md#api-contract), [§ Pagination strategy and cursor format → Filter predicates / Filter consistency](./design.md#pagination-strategy-and-cursor-format), [§ Validation rules → `GET /audit-events`](./design.md#validation-rules), [§ Layer integration → controller/ service/ persistence/](./design.md#layer-integration).
- Requirements ACs:
  - `compliance/actor-list` — comma-separated actors match any listed actor ID.
  - `compliance/actor-trim` — actor entries are trimmed before matching.
  - `compliance/actor-duplicates` — duplicate actor IDs behave as one filter value.
  - `compliance/actor-empty-entry` — empty actor entries return `400`.
  - `compliance/actor-max-ten` — more than ten raw actor entries return `422`.
  - `analyst/actor-order-pagination` — same actor ID set in different order is identical for pagination consistency.

**Scope**
- `controller/ActorFilterParser` — parse the single `actor` query parameter into a canonical immutable actor ID list: split on commas, enforce 1–10 raw entries before de-duplication, trim entries, reject empty entries, de-duplicate, and sort unique IDs lexicographically.
- `controller/AuditEventController.search(...)` — pass the canonical actor ID list into `SearchQuery`; render empty actor entries as `400 Bad Request` with `errors[0].field == "actor"` and more than ten raw actor entries as `422 Unprocessable Entity` with `errors[0].field == "actor"`.
- `service/SearchQuery` — replace the single actor string with `List<String> actorIds`.
- `persistence/AuditEventSpecifications` — replace `byActor(String)` search usage with `byActors(Collection<String>)` using an `IN` predicate against the existing `actor` column.
- Actor filtering is a single query-level OR/IN predicate, not one repository/database query per actor. No new T08 migration is required because `design.md` explicitly justifies the existing `idx_audit_events_actor_time (actor, occurred_at DESC, id DESC)` index for the bounded actor-list filter.
- Pagination cursor remains position-only; actor-list order and duplicates are normalized before querying so equal actor sets produce equal page walks without embedding filters in the token.
- README examples for `GET /audit-events` show comma-separated actor filters and the actor validation limits.

**Definition of Done**
- `./gradlew build` and `./gradlew check` green; JaCoCo ≥ 90%.
- Unit tests for `ActorFilterParser`: trims surrounding whitespace, rejects empty entries (`actor=`, `a1,,a2`, `a1,`), rejects more than ten raw entries before de-duplication, accepts duplicate IDs, and returns sorted unique IDs.
- Persistence test: `byActors(List.of("a1", "a2", "a3"))` returns rows for any listed actor and still composes with resource and time-range predicates through one `IN` predicate.
- Integration tests: happy paths for `GET /audit-events?actor=a1`, `actor=a1,a2,a3`, and an actor list containing exactly ten raw actors all return matching actors and exclude non-matching actors.
- Integration test: `GET /audit-events?actor=%20a1%20,%20a2%20` trims entries before matching.
- Integration test: duplicate actor IDs such as `actor=a1,a1,a2` behave like `actor=a1,a2`.
- Integration test: an empty actor value (`actor=`) and empty list entries return `400` with `errors[0].field == "actor"`.
- Integration test: eleven raw actor entries return `422` with `errors[0].field == "actor"`.
- Pagination integration test on a mixed filter: page 1 with `actor=a1,a2` plus another filter such as `resource=project:42`, then page 2 with the returned token and `actor=a2,a1`, yields the same remaining result set with no duplicates or gaps and excludes rows outside either filter.
- README updated with the comma-separated actor filter contract.

**Dependencies:** 07.

**Execution result:**
_(append after merge)_

---

## Coverage check (every AC has a step)

| AC | Implemented by | Covered by test in step |
|---|---|---|
| `compliance/actor-list` | 08 | 08 |
| `compliance/actor-trim` | 08 | 08 |
| `compliance/actor-duplicates` | 08 | 08 |
| `compliance/resource-filter` | 07 | 07 |
| `compliance/from-filter` | 07 | 07 |
| `compliance/to-filter` | 07 | 07 |
| `compliance/and-filters` | 07 + 08 | 07 + 08 |
| `compliance/actor-structured` | 02 | 02 |
| `compliance/resource-structured` | 02 | 02 |
| `compliance/empty-result` | 07 | 07 |
| `compliance/actor-empty-entry` | 08 | 08 |
| `compliance/actor-max-ten` | 08 | 08 |
| `compliance/from-malformed` | already in main; preserved | 07 (regression assert) |
| `compliance/to-malformed` | already in main; preserved | 07 (regression assert) |
| `compliance/from-after-to` | already in main; preserved | 07 (regression assert) |
| `sre/order-desc` | 07 | 07 |
| `sre/payload-present` | 02 | 02 |
| `sre/payload-absent` | 02 | 02 |
| `sre/context-present` | already in main; re-asserted | 02 |
| `sre/context-absent` | already in main; re-asserted | 02 |
| `analyst/pagination` | 07 | 07 |
| `analyst/cap-500` | 07 | 07 |
| `analyst/exactly-once-pagination` | 07 | 07 |
| `analyst/actor-order-pagination` | 08 | 08 |
| `analyst/beyond-end` | 07 | 07 |
| `analyst/malformed-token` | 07 | 07 |
