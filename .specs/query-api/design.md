# Query API — Design

## Decisions resolved from `requirements.md` open questions

| # | Open question | Resolution |
|---|---|---|
| 1 | `total` field in response | **Dropped.** No persona has a stated need; inherited from offset-pagination convention. |
| 2 | Pagination strategy & wire shape | **Keyset on the wire**, opaque `nextPageToken`. No `page`, no `total`. |
| 3 | Deterministic sort with tiebreaker | `ORDER BY occurred_at DESC, id DESC`. `id` (`UUID PRIMARY KEY`) is unique → total order. |
| 4 | Indexes for the filterable columns | Three composite indexes on `(filter, occurred_at DESC, id DESC)`. See § Data model. |
| 5 | `resource.id` extraction | Wire `resource.id` keeps the full string. DB stores `resource_type` in a new column. |
| 6 | `resource.type` shape | **Free-form non-blank string.** Not constrained to an enum. |
| 7 | `actor.type` values | **`USER` only** this iteration. Enum is extensible (column type is `TEXT`). |
| 8 | Legacy events | **Table is empty.** No backfill migration; new columns ship with defaults. |
| 9 | `payload` vs `context` convention | **`payload` = canonical event body** (action-specific business data, e.g. `{ amount: 100 }`). **`context` = environmental metadata** about how the action was invoked (e.g. `{ ip, userAgent, requestId }`). Both optional. |

---

## API contract

### `GET /audit-events`

Request parameters:

| Param | Type | Required | Notes |
|---|---|---|---|
| `actor` | string | no | Exact match on `actor` column (case-sensitive). |
| `resource` | string | no | Exact match on `resource` column (case-sensitive, full string). |
| `from` | ISO-8601 instant | no | Inclusive lower bound on `occurred_at`. |
| `to` | ISO-8601 instant | no | Inclusive upper bound on `occurred_at`. |
| `pageToken` | string (opaque) | no | Continuation token from the previous response. Absent ⇒ first page. |
| `size` | int | no | Default `50`. Must be >= 1; values above `500` are silently capped. |

Response (replaces today's `PagedResponse<T>` for this endpoint):

```json
{
  "items": [
    {
      "id": "01HE…Z9",
      "occurredAt": "2026-04-17T11:02:14Z",
      "actor":    { "id": "u_42",        "type": "USER"  },
      "resource": { "id": "order/9f3b…", "type": "order" },
      "action":   "order.refunded",
      "outcome":  "SUCCESS",
      "context":  { "ip": "10.0.0.1" },
      "payload":  { "amount": 100 }
    }
  ],
  "nextPageToken": "eyJ2IjoxLCJvY2N1cnJlZEF0Ijoi…"
}
```

- `nextPageToken` is **omitted** when the current page exhausts the result set (`@JsonInclude(NON_NULL)` on the DTO field).
- `context` and `payload` are omitted per-event when null.
- `resource.type` is omitted per-event when null.

**Status codes**

| Code | When |
|---|---|
| `200 OK` | Success, including empty result sets. |
| `400 Bad Request` | Malformed `from`/`to`; `from` after `to`; blank `actor`/`resource`; invalid `size`; malformed or unsupported-version `pageToken`. |
| `500 Internal Server Error` | Unexpected failure. Opaque body, full stack to log. |

### Changes to existing `POST /audit-events`

The existing ingest endpoint is updated to align with the new domain shape. **Breaking change — no backward-compatibility layer.** Status codes, error envelope, and overall request structure are unchanged; only the listed fields are affected.

Field-level changes:

| Field | Before | After |
|---|---|---|
| `actor` | `String` (e.g. `"u_42"`) | `ActorRequest { id, type? }` — `id` required (`@NotBlank`); `type` optional, defaults to `USER`. Defaulting happens while mapping the request into the domain model, not as a DTO field initializer; the service receives a domain `Actor` with a non-null type. |
| `resource` | `String` (e.g. `"order/9f3b…"`) | `ResourceRequest { id, type? }` — object optional (matches the existing nullable column); when present, `id` is required (`@NotBlank`) and `type` is optional, free-form non-blank string if provided. |
| `payload` | — (did not exist) | Optional JSON object. |

`action`, `outcome`, and `context` are untouched.

---

## Pagination strategy and cursor format

**Sort.** `ORDER BY occurred_at DESC, id DESC`. The `id DESC` tiebreaker is required: two events can share a millisecond, and without a stable tiebreaker the DB is free to return ties in any order — that would break the no-overlap / no-gap guarantee for the security analyst. `id` is `UUID PRIMARY KEY`, so it is unique and gives a total order even though UUID order carries no semantic meaning.

**Cursor.** Opaque to the client; internally base64-url of:

```json
{ "v": 1, "occurredAt": "2026-04-17T11:02:14.123Z", "id": "01HE…Z9" }
```

- `v` is a format version byte. Reject any other value with `400`.
- The cursor carries only the position on the sort key — it does **not** embed filters.

**Next-page predicate** (added on top of caller-supplied filters):

```
(occurred_at < :cursor_ts)
OR (occurred_at = :cursor_ts AND id < :cursor_id)
```

**Stability under concurrent ingest.** New events arrive with `occurred_at >= now`. Because pages are walked strictly *backwards* through `(occurred_at, id)` order, new rows are always ahead of every issued cursor and cannot appear on later pages, nor can they shift the position of older rows. No `to` pinning is required.

**Filter consistency.** The client must repeat the same `actor` / `resource` / `from` / `to` parameters on every page request. The cursor does not embed them; if the client passes different filters with a `pageToken`, the server returns results consistent with the new filters from the cursor position onward (undefined-but-not-erroring behavior — documented, not enforced).

**End of pages.** The server fetches `size + 1` rows; if a `size + 1`-th row exists, the cursor for the next page is built from the `size`-th row and the extra row is dropped from `items`. If only ≤ `size` rows are returned, `nextPageToken` is omitted.

**Malformed token.** `400 Bad Request` with the existing `{ errors: [{ field, message }] }` envelope (`field = "pageToken"`).

---

## Data model and migrations

### Migration `V4__add_actor_type_resource_type_payload.sql`

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

### Column notes

- `actor_type` is `NOT NULL DEFAULT 'USER'` — existing `INSERT` paths that do not yet set the column continue to work; new `INSERT`s set it explicitly via JPA.
- `resource_type` is **nullable** — `resource` itself is nullable, and resources written as flat strings without a `/` prefix legitimately have no type.
- `payload` is `JSONB`, nullable. No GIN index — `payload` is not a filter in this iteration.

### Index notes

- Each composite index ends with `id DESC` so PostgreSQL can satisfy `ORDER BY occurred_at DESC, id DESC` directly from the index without a separate sort step.
- No composite `(actor, resource, …)` index — combined `actor + resource` filters are rare; the planner can use either single-filter index and recheck. Revisit if profiling shows a hot path.
- No index on `actor_type` or `resource_type` — they are response fields, not filters, in this iteration.

### Backfill

None. Per resolution #8, the table is empty.

---

## Validation rules

### `GET /audit-events`

| Param | Rule |
|---|---|
| `actor` | If present, must be non-blank. → `400` on blank. |
| `resource` | If present, must be non-blank. Exact match against full `resource` column. |
| `from` | If present, must parse as ISO-8601 instant. → `400` on parse failure. |
| `to` | If present, must parse as ISO-8601 instant. → `400` on parse failure. |
| `from` + `to` | If both present, `from` must not be after `to`. → `400`. |
| `pageToken` | If present, must base64-url decode to JSON with `v == 1`, an ISO-8601 `occurredAt`, and a UUID `id`. → `400` otherwise. |
| `size` | Integer >= 1; default `50`; values above `500` are silently capped at `500`; values below `1` return `400`; non-integer values return `400` with `field = "size"`. |

### `POST /audit-events` — new/changed fields only

Rules for `action`, `outcome`, and `context` are unchanged.

| Field | Rule |
|---|---|
| `actor` | Required object. `@Valid @NotNull`. |
| `actor.id` | Required, `@NotBlank`. |
| `actor.type` | Optional. If present, must be a valid `ActorType` enum value. Defaults to `USER`. |
| `resource` | Optional object. `@Valid`. |
| `resource.id` | When `resource` is present, required, `@NotBlank`. |
| `resource.type` | Optional. If present, `@NotBlank` (free-form string). |
| `payload` | Optional, any JSON object. |

---

## Layer integration

### `domain/` (no Spring, no JPA imports)

New types:

```
domain/ActorType.java      enum ActorType { USER }
domain/Actor.java          record Actor(String id, ActorType type)
                           // compact ctor: id non-null + non-blank, type non-null
domain/Resource.java       record Resource(String id, String type)
                           // compact ctor: id non-null + non-blank; type nullable
                           //             ; type, when non-null, must be non-blank
domain/Cursor.java         record Cursor(Instant occurredAt, UUID id)
                           // compact ctor: both fields non-null
```

Updated:

- `AuditEvent` record — `String actor` → `Actor actor`; `String resource` → `Resource resource`; add `JsonNode payload` (`null` allowed).
- `NewAuditEvent` record — same changes. Validation of `Actor` / `Resource` is delegated to their compact constructors.

### `persistence/`

`AuditEventEntity`:

- Add `@Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false) private ActorType actorType;`
- Add `@Column(name = "resource_type") private String resourceType;`
- Add `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode payload;`

`AuditEventMapper`:

- **Read** — `new Actor(e.getActor(), e.getActorType())`; `e.getResource() == null ? null : new Resource(e.getResource(), e.getResourceType())`.
- **Write** — `entity.setActor(domain.actor().id())`, `entity.setActorType(domain.actor().type())`, `entity.setResource(domain.resource() == null ? null : domain.resource().id())`, `entity.setResourceType(domain.resource() == null ? null : domain.resource().type())`, `entity.setPayload(domain.payload())`.

`AuditEventArchiveEntity`: mirror the same three field additions and mapper updates.

`AuditEventSpecifications`:

- `byActor(String)` and `byResource(String)` are unchanged — they continue to match the existing `actor` / `resource` string columns. The new `actor_type` / `resource_type` columns are not filtered in this iteration.
- New helper `afterCursor(Instant ts, UUID lastId)`:

```java
public static Specification<AuditEventEntity> afterCursor(Instant ts, UUID lastId) {
    return (root, q, cb) -> cb.or(
        cb.lessThan(root.get(AuditEventEntity_.occurredAt), ts),
        cb.and(
            cb.equal(root.get(AuditEventEntity_.occurredAt), ts),
            cb.lessThan(root.get(AuditEventEntity_.id), lastId)
        )
    );
}
```

### `service/`

- `SearchQuery` — replace `int page, int size` with `Optional<Cursor> cursor, int size`.
- `AuditEventService.search(...)` — returns a new domain page type:

```
domain/KeysetPage.java  record KeysetPage<T>(List<T> items, Optional<Cursor> nextCursor)
```

Implementation: build Specification from filters, conditionally compose with `afterCursor(...)`, set `Sort.by(occurred_at DESC, id DESC)`, page with `PageRequest.of(0, size + 1)`. If `result.size() > size`, drop the extra row and build `nextCursor` from the previously-last in-range row's `(occurredAt, id)`.

### `controller/`

- `AuditEventController.search(...)` — `@RequestParam Optional<String> pageToken, @RequestParam(defaultValue = "50") int size`. Decode/encode the token in a `PageTokenCodec` bean (lives in `controller/` so the domain doesn't import `java.util.Base64`).
- New `KeysetPageResponse<T>(List<T> items, String nextPageToken)` DTO, annotated `@JsonInclude(NON_NULL)` so `nextPageToken` is omitted when null.
- New nested DTOs in `controller/dto/`:

```java
public record ActorRequest(@NotBlank String id, ActorType type) {}
public record ActorResponse(String id, ActorType type) {}
public record ResourceRequest(@NotBlank String id, String type) {}
public record ResourceResponse(String id, String type) {}
```

- `CreateAuditEventRequest` — `@NotBlank String actor` → `@Valid @NotNull ActorRequest actor`; `String resource` → `@Valid ResourceRequest resource`; add `JsonNode payload`.
- `AuditEventResponse` — `String actor` → `ActorResponse actor`; `String resource` → `ResourceResponse resource`; add `JsonNode payload` (`@JsonInclude(NON_NULL)` already applies to the response).
- `GlobalExceptionHandler` — no shape change. Add `InvalidPageTokenException` mapping → `400 Bad Request` with `errors: [{ field: "pageToken", message: ... }]`.
- `PagedResponse<T>` — keep if used by any other endpoint; otherwise remove. The query endpoint switches to `KeysetPageResponse<T>`.

---

## AGENTS.md alignment

| Invariant | How this design satisfies it |
|---|---|
| Domain purity | `Actor`, `ActorType`, `Resource`, `Cursor`, `KeysetPage` live in `domain/` with no `org.springframework.*` and no `jakarta.persistence.*` imports. Base64 stays in `controller/PageTokenCodec`. |
| Append-only | V4 migration uses `ALTER TABLE … ADD COLUMN` and `DROP INDEX` / `CREATE INDEX` only. No `UPDATE`, no `DELETE`, no row movement. Existing rows are not touched (table is empty per resolution #8). |
| Schema via Flyway only | V4 is a new migration; V1–V3 are untouched. |
| Layered architecture | DTOs in `controller/dto/`; domain types in `domain/`; flat columns at the persistence boundary, mapped in `AuditEventMapper`. Controller never reaches into `persistence/`; service mediates. |
| ArchUnit boundaries | No new cross-layer imports. The four `ArchitectureTest` rules continue to pass. |
| Breaking change acknowledged | `actor` and `resource` change shape on both the GET response and the POST request. Pagination wire shape changes from `page/size/total` to `pageToken/nextPageToken`. Call out in the PR description; consumers must update. |
| JaCoCo ≥ 90% | New unit tests: `Actor` / `Resource` / `Cursor` compact-ctor invariants, `PageTokenCodec` round-trip + bad-input cases, `KeysetPage` slicing logic. New integration tests below cover the cross-layer paths. |
| Flyway clean from empty DB | V4 only adds nullable (`resource_type`, `payload`) and defaulted (`actor_type`) columns and recreates indexes — no row-level dependencies. `FlywayMigrationIT` catches regressions. |
| Spec AC satisfied before merge (PR invariant #5) | Test coverage per AC is owned by `tasks.md`; each task lists the tests that verify the AC(s) it implements. |
