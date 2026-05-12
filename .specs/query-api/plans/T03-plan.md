# T03 — Add `Cursor` and `KeysetPage<T>` domain types (unused)

## Context

T03 is the third of seven tasks in the [`query-api`](../) spec. It introduces two new value types in `domain/` that later tasks (T04–T07) wire into the keyset-pagination plumbing:

- `Cursor` — opaque pagination position; later encoded/decoded in `controller/PageTokenCodec` (T05) and composed into a JPA `Specification` in `AuditEventSpecifications` (T04).
- `KeysetPage<T>` — return shape of `AuditEventService.search(...)` after T07.

Both types are introduced **unused** in this PR. The point is to commit them in isolation so the larger T07 wiring PR can focus on the integration without also reviewing fresh record definitions. This matches the "small, focused PRs" guidance in `AGENTS.md` § PR invariants #2.

T02 is executing in parallel. T02 modifies `AuditEvent.java` and `NewAuditEvent.java`; T03 adds **only new files** in the same package. There is no file-level overlap, so the two branches can be reviewed and merged independently. The first to merge causes a trivial fast-forward for the second.

References:
- Task definition: [`../tasks.md` § 03](../tasks.md#03--add-cursor-and-keysetpaget-domain-types-unused)
- Design: [`../design.md` § Layer integration → domain/](../design.md#layer-integration), [§ Pagination strategy and cursor format](../design.md#pagination-strategy-and-cursor-format)
- Requirements: no AC satisfied directly in this step; preparation for `analyst/pagination`, `analyst/cap-500`, `analyst/no-overlap`, `analyst/beyond-end`, `analyst/malformed-token` (all verified by T07).

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/domain/Cursor.java` | **Add.** New record. | Domain value type for the `(occurredAt, id)` sort key position. |
| `src/main/java/com/training/bartosh/auditlog/domain/KeysetPage.java` | **Add.** New generic record. | Domain return shape for keyset-paginated reads. |
| `src/test/java/com/training/bartosh/auditlog/domain/CursorTest.java` | **Add.** New unit test class. | Cover compact-ctor invariants. |
| `src/test/java/com/training/bartosh/auditlog/domain/KeysetPageTest.java` | **Add.** New unit test class. | Cover compact-ctor invariants. |

Nothing else changes. No production caller exists yet (verified: design.md explicitly defers wiring to T04–T07). No README update — these types are not user/operator-visible until T07. JaCoCo's existing line-coverage check applies; the new records are 100% covered by the new tests.

## `Cursor.java` — exact body

Matches the existing domain-record style (`AuditEvent`, `NewAuditEvent`): compact ctor, `IllegalArgumentException` with `"<field> is required"` message, argument-order checks following the record header.

```java
package com.training.bartosh.auditlog.domain;

import java.time.Instant;
import java.util.UUID;

public record Cursor(Instant occurredAt, UUID id) {

  public Cursor {
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
  }
}
```

## `KeysetPage.java` — exact body

Same style. `items` stored as the caller's reference (decision Q1: match repo — `AuditEvent` does not defensive-copy `JsonNode`; the page is short-lived). `nextCursor` is **rejected when null** (decision Q2: match `tasks.md` § 03 wording; callers must pass `Optional.empty()` explicitly).

```java
package com.training.bartosh.auditlog.domain;

import java.util.List;
import java.util.Optional;

public record KeysetPage<T>(List<T> items, Optional<Cursor> nextCursor) {

  public KeysetPage {
    if (items == null) {
      throw new IllegalArgumentException("items is required");
    }
    if (nextCursor == null) {
      throw new IllegalArgumentException("nextCursor is required");
    }
  }
}
```

## Tests

### `CursorTest.java`

Mirrors `AuditEventTest.java`: package-private class, JUnit 5, `assertThrows` per invariant, one positive happy-path test.

Test methods:

- `rejectsNullOccurredAt` — `new Cursor(null, UUID.randomUUID())` → `IllegalArgumentException`.
- `rejectsNullId` — `new Cursor(Instant.parse("2026-04-17T11:02:14.123Z"), null)` → `IllegalArgumentException`.
- `acceptsBothFields` — happy path; asserts `cursor.occurredAt()` and `cursor.id()` round-trip.

### `KeysetPageTest.java`

Test methods:

- `rejectsNullItems` — `new KeysetPage<String>(null, Optional.empty())` → `IllegalArgumentException`.
- `rejectsNullNextCursor` — `new KeysetPage<String>(List.of(), null)` → `IllegalArgumentException`.
- `acceptsEmptyItemsAndEmptyCursor` — `new KeysetPage<>(List.of(), Optional.empty())` → asserts both accessors return empty.
- `acceptsItemsWithCursor` — `new KeysetPage<>(List.of("a", "b"), Optional.of(cursor))` → asserts items size and `nextCursor().orElseThrow()` equals the cursor.

Each invariant test fails loudly if the corresponding null-check is removed from the compact ctor — required by `tasks.md` § 03 DoD.

## Definition of Done

Mirrors `tasks.md` T03 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew test --tests "*CursorTest*" --tests "*KeysetPageTest*"` passes.
- [ ] ArchUnit `ArchitectureTest.domainHasNoSpringDependencies` and `domainHasNoJpaDependencies` still pass — confirmed by `./gradlew test`. No `org.springframework.*`, no `jakarta.persistence.*`, no `org.hibernate.*` imports in either new file (manual `rg` check before commit).
- [ ] JaCoCo coverage of the two new records is 100% (both compact-ctor branches plus accessor smoke).
- [ ] No new spotless or compiler warnings; no `System.out.println`; no `TODO` without an issue reference (`AGENTS.md` § Build health #3, #7).
- [ ] No production caller references either new type — verified by `rg 'Cursor\b|KeysetPage\b' src/main/java`. Expected matches: only the two new files themselves.

## Verification — end-to-end manual

After local commit, before pushing the PR branch:

```bash
./gradlew clean build
./gradlew test --tests "*CursorTest*" --tests "*KeysetPageTest*" --info
rg --files-with-matches 'Cursor\b|KeysetPage\b' src/main/java
```

Expected:
- First two commands: green.
- Third command: lists only `Cursor.java` and `KeysetPage.java` (confirms no accidental wiring leaked from T02 work-in-progress).

Optional ArchUnit-targeted check:

```bash
./gradlew test --tests "*ArchitectureTest*"
```

Expected: green; the four rules in `ArchitectureTest.java` continue to hold.

## Out of scope (deferred to later tasks)

- `afterCursor` JPA specification helper that consumes `Cursor` — T04.
- `PageTokenCodec` / `InvalidPageTokenException` that encode/decode `Cursor` on the wire — T05.
- `KeysetPageResponse<T>` DTO that wraps `KeysetPage` for HTTP — T06.
- Wiring `KeysetPage` through `SearchQuery` / `AuditEventService.search(...)` / `AuditEventController.search(...)` and deleting `PagedResponse<T>` — T07.

The two new types are unreferenced in production after this PR lands; this is intentional and verified by the `rg` check in the DoD.

## Open questions

None.

- Q1 (`items` storage): **Store reference** — matches repo style; `AuditEvent` does not defensive-copy `JsonNode`.
- Q2 (`nextCursor` null handling): **Reject null** — matches the explicit `tasks.md` § 03 wording and the loud-failure preference for programmer errors.

## Branch & PR

- **Branch:** `query-api/t03-cursor-keysetpage` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3). If T02 has merged by then, the fast-forward picks up its `AuditEvent` / `NewAuditEvent` changes automatically — no conflict because T03 only adds new files.
- **PR title:** `feat(query-api): add Cursor and KeysetPage domain types`
- **PR description:**
  - Maps DoD to evidence (test names; `./gradlew build` link).
  - Notes that both types are intentionally unused; cites T04–T07 as the consumers.
  - States explicitly that no README update is needed (no user/operator-visible change) — `AGENTS.md` § PR invariant #4.
  - Confirms ACs: none satisfied directly in this PR (preparation step); the spec coverage table in `tasks.md` already attributes the analyst-* ACs to T07.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 03 in `tasks.md` (per the `_(append after merge)_` placeholder).
