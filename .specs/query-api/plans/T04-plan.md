# T04 — Add `afterCursor` specification helper (unused)

## Context

T04 is the fourth of seven tasks in the [`query-api`](..) spec. It is an **additive** persistence-layer step that lands the predicate the eventual keyset paging will use, with a real-Postgres test that locks in both branches of the predicate. No production code is wired to call it yet — `afterCursor` exists alongside the other four specs in `AuditEventSpecifications` and is invoked only by the new IT. T07 will wire it through `AuditEventService.search(...)`.

This is a small, isolated step (one new static method + one new IT file).

### Dependency note (deviation from `tasks.md`)

`tasks.md` lists T04's dependency as T03. **At the source-code level, T04 is compile-independent of T03 / T02 / T01:**
- The signature is `afterCursor(Instant ts, UUID lastId)` — primitives, not the `Cursor` record from T03.
- The body references `AuditEventEntity_.occurredAt` and `AuditEventEntity_.id`, both of which exist in the current generated metamodel (verified during planning).
- The seed entities in the new IT can be built with the *current* `AuditEventEntity` constructor (7 args, pre-T02) or the post-T02 11-arg constructor — either signature works because the spec only queries `occurredAt` and `id`.

The `tasks.md` dependency on T03 is therefore a **conceptual ordering hint** (T03–T06 are all preparation for T07), not a hard compile dependency. T04 can land independently. If T03 has already merged, no adjustment is needed.

References:
- Task definition: [`../tasks.md` § 04](../tasks.md#04--add-aftercursor-specification-helper-unused)
- Design: [`../design.md` § Layer integration → persistence/ → `AuditEventSpecifications`](../design.md#layer-integration), [§ Pagination strategy → Next-page predicate](../design.md#pagination-strategy-and-cursor-format)
- ACs *prepared for* (not satisfied here): `analyst/no-overlap`, `analyst/beyond-end` — both verified end-to-end in T07.

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/persistence/AuditEventSpecifications.java` | **Modify.** Add `public static Specification<AuditEventEntity> afterCursor(Instant ts, UUID lastId)` alongside the four existing specs. | Implements the keyset next-page predicate from design.md. |
| `src/integrationTest/java/com/training/bartosh/auditlog/persistence/AuditEventSpecificationsIT.java` | **Add.** New IT verifying both branches of `afterCursor`'s `OR` predicate. | DoD requires real-Postgres assertions that both the strict-`<` branch and the `=` + `<id` tiebreaker branch are exercised. |

Nothing else changes. No new imports beyond `java.time.Instant` and `java.util.UUID` (both already in the file). `AuditEventEntity_` is unchanged — the metamodel already exposes `occurredAt` and `id`. `README.md` requires no update — `afterCursor` is internal persistence machinery with no user-facing impact in this PR.

## `AuditEventSpecifications` addition — exact body

Append to `src/main/java/com/training/bartosh/auditlog/persistence/AuditEventSpecifications.java`, after the four existing methods:

```java
public static Specification<AuditEventEntity> afterCursor(Instant ts, UUID lastId) {
  return (root, q, cb) ->
      cb.or(
          cb.lessThan(root.get(AuditEventEntity_.occurredAt), ts),
          cb.and(
              cb.equal(root.get(AuditEventEntity_.occurredAt), ts),
              cb.lessThan(root.get(AuditEventEntity_.id), lastId)));
}
```

Body taken verbatim from [`../design.md` § Layer integration](../design.md#layer-integration) — no deviation. Style matches the four existing lambda-based specs in the same file. Imports needed: `java.time.Instant`, `java.util.UUID` — `Instant` is already imported in the existing file (used by `occurredAtOrAfter` / `occurredAtOrBefore`); `UUID` may or may not be — add if missing.

No null checks on `ts` or `lastId`. The spec is package-internal; callers (T07's service layer) are responsible for providing non-null cursor values. This matches the existing convention in the file (no null guards on `byActor`, `byResource`, etc.).

## `AuditEventSpecificationsIT.java` — new file

Path: `src/integrationTest/java/com/training/bartosh/auditlog/persistence/AuditEventSpecificationsIT.java`.

Pattern: extends `AuditLogIntegrationTest` (matches `FlywayMigrationIT` and `AuditEventImmutabilityIT`). Seeds via `repository.save(...)` then `em.flush(); em.clear()` to force subsequent reads to hit the DB. Asserts via `repository.findAll(spec, Pageable)` with the keyset sort applied.

```java
package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import com.training.bartosh.auditlog.domain.Outcome;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class AuditEventSpecificationsIT extends AuditLogIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");
  private static final Instant T1 = Instant.parse("2026-05-01T11:00:00Z");
  private static final Instant T2 = Instant.parse("2026-05-01T12:00:00Z");

  @Autowired private AuditEventRepository repository;
  @Autowired private EntityManager em;

  @Test
  void afterCursorReturnsRowsWithStrictlyEarlierOccurredAt() {
    UUID idAtT0 = UUID.randomUUID();
    UUID idAtT1 = UUID.randomUUID();
    UUID idAtT2 = UUID.randomUUID();
    seed(idAtT0, T0);
    seed(idAtT1, T1);
    seed(idAtT2, T2);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T2, idAtT2),
                PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
                        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID))))
            .getContent();

    assertEquals(2, result.size(), "T0 and T1 rows should be returned");
    assertEquals(idAtT1, result.get(0).getId(), "T1 first (DESC order)");
    assertEquals(idAtT0, result.get(1).getId(), "T0 second");
  }

  @Test
  void afterCursorTiebreakerReturnsRowsWithSameOccurredAtAndSmallerId() {
    UUID smaller = uuidWithMostSignificantBits(1L);
    UUID cursor  = uuidWithMostSignificantBits(2L);
    UUID larger  = uuidWithMostSignificantBits(3L);
    seed(smaller, T1);
    seed(cursor,  T1);
    seed(larger,  T1);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T1, cursor),
                PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
                        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID))))
            .getContent();

    assertEquals(1, result.size(), "only the row with id < cursor at the same instant returns");
    assertEquals(smaller, result.get(0).getId());
  }

  @Test
  void afterCursorExcludesCursorRowAndAllLaterRows() {
    UUID idAtT0 = UUID.randomUUID();
    UUID idAtT1 = UUID.randomUUID();
    UUID idAtT2 = UUID.randomUUID();
    seed(idAtT0, T0);
    seed(idAtT1, T1);
    seed(idAtT2, T2);
    em.flush();
    em.clear();

    // Cursor at T0 / idAtT0 — only rows strictly before this position should return.
    // Nothing is before it, so the result must be empty.
    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T0, idAtT0),
                PageRequest.of(0, 10))
            .getContent();

    assertTrue(result.isEmpty(), "no rows are positioned before the earliest cursor");
  }

  private void seed(UUID id, Instant occurredAt) {
    repository.save(
        new AuditEventEntity(
            id, occurredAt, "u-" + id, "user.login", null, Outcome.SUCCESS, null));
  }

  private static UUID uuidWithMostSignificantBits(long msb) {
    return new UUID(msb, 0L);
  }
}
```

**Notes on the test:**

- `AuditLogIntegrationTest` is `@Transactional`, so each test rolls back automatically — no manual cleanup.
- `em.flush(); em.clear()` after seeding forces the subsequent `findAll` to hit the DB rather than the persistence context cache. Matches `RetentionServiceIT`.
- The Sort is built from the `AuditEventEntity_` metamodel string constants (`OCCURRED_AT`, `ID`) so renames stay refactor-safe.
- `uuidWithMostSignificantBits` produces deterministic UUIDs whose natural ordering matches `msb` (UUID compares high-bits-first), so the tiebreaker test has a known ordering. Without this, two random UUIDs could compare either way and the assertion would flake.
- **Post-T02 entity constructor.** If T02 has merged before T04, `new AuditEventEntity(...)` will require 10 args (adds `actorType`, `resourceType`, `payload`). The plan's `seed(...)` helper needs to be updated then — pass `ActorType.USER`, `null`, `null` after `actor`. Note the path lookup uses `AuditEventEntity_.actor` / `_.resource` / `_.id` which are stable across T02.

## Definition of Done

Mirrors `tasks.md` T04 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew integrationTest --tests "*AuditEventSpecificationsIT"` passes — three new tests green:
  - `afterCursorReturnsRowsWithStrictlyEarlierOccurredAt` — exercises the strict-`<` branch.
  - `afterCursorTiebreakerReturnsRowsWithSameOccurredAtAndSmallerId` — exercises the `=` + `<id` tiebreaker branch.
  - `afterCursorExcludesCursorRowAndAllLaterRows` — boundary check: cursor row itself is not returned.
- [ ] Existing tests in `src/integrationTest/java/.../persistence/` (`FlywayMigrationIT`, `AuditEventImmutabilityIT`) continue to pass — no shared state, no schema changes.
- [ ] ArchUnit boundary tests (`*ArchitectureTest`) continue to pass — `AuditEventSpecifications` already lives in `persistence/` and the new method introduces no new cross-layer imports.
- [ ] No new compiler warnings; spotless clean; no `TODO` without an issue reference.
- [ ] PR description maps the AC-preparation status of T04 explicitly: T04 implements the predicate but does not satisfy any AC on its own; satisfaction is in T07 (per `AGENTS.md` § PR invariant #5, "If an AC is intentionally deferred, mark it explicitly").

## Verification — end-to-end manual

```bash
./gradlew clean build
./gradlew integrationTest --tests "*AuditEventSpecificationsIT" --info
```

Both green. The `--info` run prints the Hibernate SQL for each `findAll` call; tail it to visually confirm the generated WHERE clause contains the disjunction:

```sql
WHERE occurred_at < ? OR (occurred_at = ? AND id < ?)
```

with the keyset sort `ORDER BY occurred_at DESC, id DESC`. This is a one-time sanity check that the spec compiled into the expected SQL, not a permanent assertion.

No `psql`-level verification needed — the IT exercises the predicate against a real Testcontainers Postgres.

## Out of scope (deferred to later tasks)

- Calling `afterCursor` from anywhere in production code — T07 wires it through `AuditEventService.search(...)`.
- The `Cursor` domain record that wraps `(Instant occurredAt, UUID id)` — T03.
- `PageTokenCodec` to encode/decode the cursor on the wire — T05.
- `KeysetPageResponse<T>` DTO — T06.
- Removing `PagedResponse<T>` — T07.

## Open questions

None. The predicate body is verbatim from `design.md`; the IT pattern matches `RetentionServiceIT` and `FlywayMigrationIT` conventions; the dependency-order observation above is informational only.

## Branch & PR

- **Branch:** `query-api/t04-after-cursor-spec` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3).
- **PR title:** `feat(query-api): add afterCursor keyset predicate (unused)`
- **PR description:** State explicitly that no AC is satisfied by this PR; T07 will satisfy `analyst/no-overlap` and `analyst/beyond-end` once `afterCursor` is wired through the service. Link to design.md § Pagination strategy → Next-page predicate.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 04 in `tasks.md`.
