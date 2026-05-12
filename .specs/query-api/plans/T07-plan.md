# T07 — Wire keyset pagination through service + controller; drop `PagedResponse`

## Context

T07 is the seventh and final task in the [`query-api`](..) spec. It is the **wiring** step that culminates the keyset work: `AuditEventService.search(...)` switches from offset-paged `Page<AuditEvent>` to `KeysetPage<AuditEvent>`, `AuditEventController.search(...)` swaps `page`/`size` query params for `pageToken`/`size` and returns `KeysetPageResponse<T>`, `PagedResponse<T>` is deleted, and the GET endpoint's compliance-officer + security-analyst ACs are satisfied end-to-end for the first time.

This PR also lands cross-cutting validation that has been missing from the GET endpoint to date:
- `from` after `to` → `400` (not enforced today).
- Blank `actor` / `resource` → `400` (not enforced today).
- Malformed `from` / `to` ISO-8601 → `400` with the app's `{ errors: [{ field, message }] }` envelope (today Spring returns its default error body, not the envelope).
- Malformed `pageToken` → `400` with the app's envelope.

Per planning decision, the cross-field validations (`from > to`, blank filter values) live in **`SearchQuery`'s compact ctor** — a single source of truth, throwing `IllegalArgumentException` which `GlobalExceptionHandler` already maps to `400` with `{ errors: [{ message }] }` (no `field`, consistent with the existing non-bean-validation convention).

T07 depends on (planned, not yet merged):
- **T02** — structured `actor`/`resource`/`payload` end-to-end.
- **T03** — `Cursor` and `KeysetPage<T>` domain types.
- **T04** — `afterCursor` specification helper.
- **T05** — `PageTokenCodec` + `InvalidPageTokenException`.
- **T06** — `KeysetPageResponse<T>` DTO.

If any of those have not yet landed at execution time, T07 cannot compile — split out the missing prep into its own PR(s) first.

References:
- Task definition: [`../tasks.md` § 07](../tasks.md#07--wire-keyset-pagination-through-service--controller-drop-pagedresponse)
- Design: [`../design.md` § API contract → `GET /audit-events`](../design.md#api-contract), [§ Pagination strategy and cursor format](../design.md#pagination-strategy-and-cursor-format), [§ Layer integration → service/, controller/](../design.md#layer-integration)
- ACs implemented end-to-end in this PR:
  - `compliance/combined-filters` — re-asserted under keyset.
  - `compliance/empty-result` — `200 OK` with `items: []` and `nextPageToken` omitted.
  - `compliance/from-malformed`, `compliance/to-malformed` — `400` with app envelope.
  - `compliance/from-after-to` — `400`.
  - `sre/order-desc` — most-recent-first under `(occurredAt DESC, id DESC)`.
  - `analyst/pagination` — multi-page walk.
  - `analyst/cap-500` — `size` silently capped at 500.
  - `analyst/no-overlap` — stable under concurrent insert.
  - `analyst/beyond-end` — `200` with `items: []`.
  - `analyst/malformed-token` — `400` with `errors[*].field == "pageToken"`.

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/service/SearchQuery.java` | **Modify.** Add `Optional<Cursor> cursor` and `int size` fields; add compact ctor with blank-string, range, and size invariants. | Design.md § Layer integration → service/; planning decision (cross-field validation in compact ctor). |
| `src/main/java/com/training/bartosh/auditlog/service/AuditEventService.java` | **Modify.** `search(...)` signature collapses to `KeysetPage<AuditEvent> search(SearchQuery)`. Internal logic: build filter `Specification`, conditionally compose `afterCursor`, fetch `size + 1` rows with `Sort.by(DESC, occurred_at).and(DESC, id)`, drop the extra row, build `nextCursor`. | Design.md § Layer integration → service/. |
| `src/main/java/com/training/bartosh/auditlog/controller/AuditEventController.java` | **Modify.** Replace `page`, `size` params with `Optional<String> pageToken` and `int size` (default `50`, silent cap at `500`). Decode token via `PageTokenCodec`. Returns `KeysetPageResponse<AuditEventResponse>`. | Design.md § API contract; § Layer integration → controller/. |
| `src/main/java/com/training/bartosh/auditlog/controller/GlobalExceptionHandler.java` | **Modify.** Add `handleTypeMismatch(...)` override that renders `MethodArgumentTypeMismatchException` (raised by `@DateTimeFormat` parse failures) as `400` with `{ errors: [{ field, message }] }`, with `field` set to the failing parameter name. | Today this exception falls through to Spring's default; design.md mandates the app's envelope for `compliance/from-malformed` and `compliance/to-malformed`. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/PagedResponse.java` | **Delete.** Confirmed via `rg "PagedResponse"`: only `AuditEventController.search(...)` references it, and that call site is rewritten in this PR. | Design.md § Layer integration → controller/ → "`PagedResponse<T>` — keep if used by any other endpoint; otherwise remove." |
| `src/test/java/com/training/bartosh/auditlog/service/AuditEventServiceTest.java` | **Modify.** Delete `searchPreservesCallerSortWhenAlreadySpecified` (sort is now internal — no caller-supplied sort exists). Add 2–3 service-level unit tests that mock the repository and assert the size-+1 over-fetch semantics and `nextCursor` build logic. | Existing test is obsolete; new tests pin the keyset slicing logic at the unit boundary. |
| `src/integrationTest/java/com/training/bartosh/auditlog/controller/AuditEventControllerIT.java` | **Modify + extend.** Update existing tests that asserted offset shape (`$.size`, `$.total`). Add new integration tests (listed below) covering every AC this PR implements. | DoD coverage table in `tasks.md` § 07 enumerates them. |
| `README.md` | **Modify.** Replace the GET `/audit-events` parameter table (`page`, `size`) with (`pageToken`, `size`). Replace the response example to show `items` + `nextPageToken` (omitted on last page). Update the "sorted by `occurredAt DESC`" line to `(occurredAt DESC, id DESC)`. | `AGENTS.md` § PR invariant #4. |

ArchUnit boundary tests (`src/test/java/.../architecture/ArchitectureTest.java`) are not edited — `PageTokenCodec` lives in `controller/`, `Cursor`/`KeysetPage` in `domain/`, `afterCursor` in `persistence/`; no new cross-layer imports.

## `SearchQuery` — new shape

```java
package com.training.bartosh.auditlog.service;

import com.training.bartosh.auditlog.domain.Cursor;
import java.time.Instant;
import java.util.Optional;

public record SearchQuery(
    String actor,
    String resource,
    Instant from,
    Instant to,
    Optional<Cursor> cursor,
    int size) {

  public SearchQuery {
    if (actor != null && actor.isBlank()) {
      throw new IllegalArgumentException("actor must be non-blank when present");
    }
    if (resource != null && resource.isBlank()) {
      throw new IllegalArgumentException("resource must be non-blank when present");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    if (cursor == null) {
      throw new IllegalArgumentException("cursor must not be null (use Optional.empty())");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be >= 1");
    }
  }
}
```

Note: `size` is *not* clamped to ≤500 in the compact ctor — clamping is the controller's responsibility (silent cap per design.md `analyst/cap-500`). The compact ctor enforces only the lower bound as a defensive invariant; the controller's clamping guarantees the upper bound is never violated.

## `AuditEventService.search(...)` — new impl

```java
@Transactional(readOnly = true)
public KeysetPage<AuditEvent> search(SearchQuery query) {
  List<Specification<AuditEventEntity>> specs = new ArrayList<>();
  if (query.actor() != null)    specs.add(AuditEventSpecifications.byActor(query.actor()));
  if (query.resource() != null) specs.add(AuditEventSpecifications.byResource(query.resource()));
  if (query.from() != null)     specs.add(AuditEventSpecifications.occurredAtOrAfter(query.from()));
  if (query.to() != null)       specs.add(AuditEventSpecifications.occurredAtOrBefore(query.to()));
  query.cursor().ifPresent(c -> specs.add(AuditEventSpecifications.afterCursor(c.occurredAt(), c.id())));
  Specification<AuditEventEntity> spec = Specification.allOf(specs);

  Sort sort =
      Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
          .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID));

  List<AuditEventEntity> rows =
      repository.findAll(spec, PageRequest.of(0, query.size() + 1, sort)).getContent();

  if (rows.size() <= query.size()) {
    return new KeysetPage<>(
        rows.stream().map(AuditEventMapper::toDomain).toList(),
        Optional.empty());
  }
  List<AuditEventEntity> page = rows.subList(0, query.size());
  AuditEventEntity last = page.get(page.size() - 1);
  return new KeysetPage<>(
      page.stream().map(AuditEventMapper::toDomain).toList(),
      Optional.of(new Cursor(last.getOccurredAt(), last.getId())));
}
```

The old `withDefaultSort(...)` helper becomes dead code — delete it. The `Clock` dependency stays for `record(...)`.

## `AuditEventController.search(...)` — new impl

```java
private static final int DEFAULT_SIZE = 50;
private static final int MAX_PAGE_SIZE = 500;

@GetMapping
public KeysetPageResponse<AuditEventResponse> search(
    @RequestParam(required = false) String actor,
    @RequestParam(required = false) String resource,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to,
    @RequestParam(required = false) Optional<String> pageToken,
    @RequestParam(defaultValue = "50") int size) {

  if (size < 1) {
    throw new IllegalArgumentException("size must be >= 1");
  }
  int cappedSize = Math.min(size, MAX_PAGE_SIZE);
  Optional<Cursor> cursor = pageToken.map(pageTokenCodec::decode);  // throws InvalidPageTokenException
  KeysetPage<AuditEvent> result =
      service.search(new SearchQuery(actor, resource, from, to, cursor, cappedSize));

  List<AuditEventResponse> items = result.items().stream().map(AuditEventResponse::from).toList();
  String nextToken = result.nextCursor().map(pageTokenCodec::encode).orElse(null);
  return new KeysetPageResponse<>(items, nextToken);
}
```

`pageTokenCodec` is the bean introduced in T05. The controller field is injected via constructor alongside the existing `AuditEventService`.

`size` is silently capped only on the upper bound (no `400` for over-large requests, per `analyst/cap-500`). Negative or zero `size` is invalid and returns `400`; non-integer `size` is handled by `handleTypeMismatch(...)` and returns `400` with `field = "size"`. `pageToken.map(codec::decode)` propagates `InvalidPageTokenException` for `400` via the handler T05 installed.

## `GlobalExceptionHandler` — `handleTypeMismatch` override

Append to `GlobalExceptionHandler`:

```java
@Override
protected ResponseEntity<Object> handleTypeMismatch(
    TypeMismatchException ex,
    HttpHeaders headers,
    HttpStatusCode status,
    WebRequest request) {
  String field =
      ex instanceof MethodArgumentTypeMismatchException matm ? matm.getName() : "";
  String message = "must be a valid value for " + (field.isEmpty() ? "parameter" : field);
  return ResponseEntity.badRequest()
      .body(Map.of("errors", List.of(Map.of("field", field, "message", message))));
}
```

This is the only addition needed for `compliance/from-malformed` and `compliance/to-malformed` to render the app envelope. The Javadoc on the class already states the envelope contract — no doc update needed.

## `AuditEventServiceTest` — unit test updates

**Delete:** `searchPreservesCallerSortWhenAlreadySpecified` — sort is internal; no caller-supplied sort exists. The behavior it tested is gone.

**Add (mocking `AuditEventRepository`, asserting via `ArgumentCaptor<Pageable>`):**

1. `searchFetchesSizePlusOneRowsForKeysetWindow` — given `size=10`, assert the captured `Pageable` has `pageSize == 11`.
2. `searchOmitsNextCursorWhenRepositoryReturnsExactlySize` — mock returns 10 rows for `size=10`; assert `result.nextCursor()` is `Optional.empty()`.
3. `searchReturnsNextCursorBuiltFromLastInRangeRow` — mock returns 11 rows for `size=10`; assert `result.items()` has 10 entries and `result.nextCursor()` carries the 10th row's `(occurredAt, id)`.

These three pin the slicing logic at the unit boundary; the rest of the keyset behavior is covered by ITs.

## `AuditEventControllerIT` — IT additions & updates

**Update existing tests that asserted offset shape:**

- `getRespectsPaginationLimits` — was asserting `$.size == 2` and `$.total == 3`. **Rename to `getRespectsSizeRequestWhenLessThanCap`** and replace assertions with: response body has `$.items` length 2; `$.size` and `$.total` are absent; `$.nextPageToken` exists.
- All other `getFilters*` tests — JSON path `$.items[...]` stays valid (carried over). No `$.page`/`$.total`/`$.size` assertions remain.

**Add new tests, alongside `AuditEventControllerIT` in the same file (per the SPEC_WORKFLOW IT-location decision):**

| Test method | Covers AC | What it asserts |
|---|---|---|
| `getReturnsEmptyResultWith200AndNoNextPageToken` | `compliance/empty-result` | GET with filters matching zero rows → `200`, `$.items == []`, `$.nextPageToken` absent. |
| `getRejectsFromAfterTo` | `compliance/from-after-to` | `?from=…2026-01-02…&to=…2026-01-01…` → `400`, response has `errors[*].message` containing "from must not be after to". |
| `getRejectsMalformedFrom` | `compliance/from-malformed` | `?from=not-a-date` → `400`, `errors[*].field == "from"`. |
| `getRejectsMalformedTo` | `compliance/to-malformed` | `?to=not-a-date` → `400`, `errors[*].field == "to"`. |
| `getRejectsBlankActor` | implied by design.md validation | `?actor=` or `?actor=%20` → `400`. |
| `getRejectsBlankResource` | implied by design.md validation | `?resource=` or `?resource=%20` → `400`. |
| `getRejectsInvalidSize` | implied by design.md validation | `?size=0`, `?size=-1`, and `?size=abc` → `400`; non-integer case has `errors[*].field == "size"`. |
| `getReturnsEventsMostRecentFirst` | `sre/order-desc` | Seed three rows at T0, T1, T2 (with distinct fixed clocks); GET returns them T2 → T1 → T0. |
| `getWalksMultiplePages` | `analyst/pagination` | Seed > size rows; iterate pages via `nextPageToken`; assert union equals seeded set, no duplicates, no missing rows. |
| `getCapsSizeAt500EvenWhenLarger` | `analyst/cap-500` | `?size=10000` → response has at most 500 items; no error. Easiest: seed 600 rows (acceptable in IT runtime), then assert `$.items.length() == 500` and `$.nextPageToken` is present. |
| `getIsStableUnderConcurrentInsert` | `analyst/no-overlap` | Seed N=3 rows at T0–T2. Walk page 1 (size=2). Insert a fresh row at "now" (after T2). Walk page 2 using page 1's `nextPageToken`. Assert: combined ids from page 1 ∪ page 2 == {idAtT0, idAtT1, idAtT2}; the new row's id appears on neither page. |
| `getReturnsEmptyForCursorBeyondEnd` | `analyst/beyond-end` | Construct a `pageToken` for a cursor earlier than the earliest row in the seeded set; GET → `$.items == []`, `$.nextPageToken` absent. |
| `getRejectsMalformedPageToken` | `analyst/malformed-token` | `?pageToken=not-base64` → `400`, `errors[*].field == "pageToken"`. Also test `?pageToken=` of a valid base64 string that decodes to JSON with `v=2` → same outcome (verifies `InvalidPageTokenException` raised from the codec). |

**Helper change in `seed(...)`:** Already takes nested-actor JSON post-T02. No change in T07.

**Helper addition:** Add a small helper to construct a `pageToken` programmatically for the malformed-token and beyond-end tests — `@Autowired PageTokenCodec codec` and call `encode(new Cursor(...))` with a fabricated cursor.

## README — pagination section rewrite

Existing section (README.md:62–84) is offset-paged. Rewrite to:

```markdown
### `GET /audit-events`

Search events. All filters optional; results sorted by `(occurredAt DESC, id DESC)`.

| Param | Type | Default | Notes |
| --- | --- | --- | --- |
| `actor` | string | — | Exact match. Non-blank when present. |
| `resource` | string | — | Exact match. Non-blank when present. |
| `from` | ISO-8601 instant | — | Inclusive lower bound on `occurredAt`. |
| `to` | ISO-8601 instant | — | Inclusive upper bound on `occurredAt`. |
| `pageToken` | string (opaque) | — | Continuation token from a previous response. Absent ⇒ first page. |
| `size` | int | 50 | Must be >= 1; silently capped at 500. |

Response: `200 OK` with

```json
{
  "items": [{ "...event..." }],
  "nextPageToken": "eyJ2IjoxLCJvY2N1cnJlZEF0Ijoi…"
}
```

`nextPageToken` is omitted when the current page exhausts the result set. The client must repeat the same filters on every subsequent request — the token carries only the position on the sort key.
```

The curl snippet immediately below also needs a refresh — drop the `page=0` example, add a follow-up `curl` with `pageToken=…`.

## Definition of Done

Mirrors `tasks.md` T07 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew test --tests "*AuditEventServiceTest"` passes — three new keyset-slicing unit tests green; the deleted offset test is gone.
- [ ] `./gradlew integrationTest --tests "*AuditEventControllerIT"` passes — all 13 new/updated IT methods green:
  - `getReturnsEmptyResultWith200AndNoNextPageToken`
  - `getRejectsFromAfterTo`
  - `getRejectsMalformedFrom`, `getRejectsMalformedTo`
  - `getRejectsBlankActor`, `getRejectsBlankResource`
  - `getRejectsInvalidSize`
  - `getReturnsEventsMostRecentFirst`
  - `getWalksMultiplePages`
  - `getCapsSizeAt500EvenWhenLarger`
  - `getIsStableUnderConcurrentInsert`
  - `getReturnsEmptyForCursorBeyondEnd`
  - `getRejectsMalformedPageToken`
  - plus the renamed `getRespectsSizeRequestWhenLessThanCap`
- [ ] `./gradlew test --tests "*ArchitectureTest"` passes — no new cross-layer imports.
- [ ] `rg "PagedResponse"` returns zero matches in `src/main/`. The `PagedResponse.java` file is deleted.
- [ ] No new compiler warnings; spotless clean; no `TODO` without an issue reference; no `System.out.println`.
- [ ] README's GET-endpoint section reflects the new shape (verified by re-reading after edit).
- [ ] PR description maps each AC implemented in this step to the named IT method (per `AGENTS.md` § PR invariant #5). The coverage table in `tasks.md` § "Coverage check" is the source of truth.

## Verification — end-to-end manual

```bash
./gradlew clean build
./gradlew integrationTest --tests "*AuditEventControllerIT" --info
```

Both green. Optional manual smoke against a running server:

```bash
./gradlew bootRun &
sleep 5
# seed a few events
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8080/audit-events \
    -H 'Content-Type: application/json' \
    -d "{\"actor\":{\"id\":\"u$i\"},\"action\":\"user.login\",\"outcome\":\"SUCCESS\"}" > /dev/null
done
# walk pages
TOKEN=$(curl -s 'http://localhost:8080/audit-events?size=2' | jq -r .nextPageToken)
echo "page 1 token: $TOKEN"
curl -s "http://localhost:8080/audit-events?size=2&pageToken=$TOKEN" | jq
# malformed token
curl -i 'http://localhost:8080/audit-events?pageToken=not-base64'
# malformed from
curl -i 'http://localhost:8080/audit-events?from=not-a-date'
```

Expected: walk returns paginated results, last page omits `nextPageToken`. Malformed inputs return `400` with `{ errors: [{ field, message }] }`.

## Out of scope

- Filtering by `actor.type` or `resource.type` (explicit "out of scope" in `requirements.md`).
- Sorting on fields other than `occurredAt` (explicit "out of scope").
- Multi-value filters (e.g. `?actor=u_1&actor=u_2`) — explicit "out of scope".
- Any retroactive change to `POST /audit-events` shape (T02 handled that).

## Open questions

None. The cross-field validation location is resolved (compact ctor). The date-format handler addition is owned by T07 (no other task ever satisfies the GET error ACs).

## Branch & PR

- **Branch:** `query-api/t07-keyset-wire` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3). Confirm T02, T03, T04, T05, T06 have all merged first — T07 will not compile otherwise.
- **PR title:** `feat(query-api): keyset pagination end-to-end; drop PagedResponse (breaking)`
- **PR description:** Map each AC to its IT. Call out the breaking change explicitly — GET wire shape changes (`page`/`size`/`total` → `pageToken`/`size` + `nextPageToken`); consumers must update. Link to design.md § Pagination strategy for the rationale on stability under concurrent insert.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 07 in `tasks.md`. With T07 complete, the entire `query-api` spec is delivered.
