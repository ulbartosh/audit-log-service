# T08 — Multi-Actor Filter Plan

## Context

T08 implements comma-separated actor filters for `GET /audit-events` on branch
`query-api/t08-actor-list-filter`.

The task stays one AGENTS-compliant task, branch, and PR. To keep review and
rollback safe, implement it as several small commits inside that PR. Each commit
should leave the project compiling and should avoid unrelated refactors.

References:

- Task definition: [`../tasks.md` § 08](../tasks.md#08--comma-separated-actor-list-filters)
- Requirements: [`../requirements.md` § Filter semantics](../requirements.md#filter-semantics),
  [`../requirements.md` § US-1](../requirements.md#us-1-assembled-intelligence-officer--confirm-or-refute-an-action-during-an-audit),
  [`../requirements.md` § US-3](../requirements.md#us-3-security-intelligence-analyst--paginate-a-large-result-set-without-loss-or-duplication)
- Design: [`../design.md` § API contract](../design.md#api-contract),
  [`../design.md` § Pagination strategy and cursor format](../design.md#pagination-strategy-and-cursor-format),
  [`../design.md` § Validation rules](../design.md#validation-rules),
  [`../design.md` § Layer integration](../design.md#layer-integration)

## Safe Commit 1 — Parser and Actor Error Mapping

Add the actor-list parsing boundary without changing the service query path yet.

Implementation:

- Add `controller/ActorFilterParser`.
- Parse a single raw `actor` query parameter into a canonical immutable `List<String>`.
- Split on commas, enforce the raw-entry cap before de-duplication, trim entries,
  reject empty entries, de-duplicate, and sort lexicographically.
- Add actor-filter exception handling:
  - Empty actor values/list entries return `400 Bad Request`.
  - More than ten raw actor entries return `400 Bad Request`.
  - Both error bodies include `errors[0].field == "actor"`.

Tests:

- Unit tests for `ActorFilterParser` covering:
  - one actor,
  - three actors,
  - exactly ten actors,
  - surrounding whitespace trim,
  - duplicate values,
  - sorted unique output,
  - empty values: `actor=`, `a1,,a2`, `a1,`,
  - eleven raw values.
- Handler tests for actor parse errors (empty entries and more than ten raw entries) mapping to `400`.

Verification after commit:

- `rtk ./gradlew test`

## Safe Commit 2 — Service and Persistence Actor List Contract

Move the internal query model from one actor string to a canonical actor-id list.

Implementation:

- Change `SearchQuery` from `String actor` to `List<String> actorIds`.
- In the `SearchQuery` compact constructor, reject a null actor list and
  defensively copy it into an immutable list.
- Replace `AuditEventSpecifications.byActor(String)` with
  `byActors(Collection<String>)`.
- Implement `byActors(...)` as a single `IN` predicate against the existing
  `actor` column.
- Update `AuditEventService.search(...)` to compose `byActors(...)` only when
  `actorIds` is non-empty.
- Preserve current resource, time-range, cursor, size, and sort behavior.

Tests:

- Update service unit tests to construct `SearchQuery` with `List.of()`.
- Add/adjust service tests to prove empty actor lists do not add an actor
  predicate and non-empty actor lists are accepted.
- Add a persistence integration test proving
  `byActors(List.of("a1", "a2", "a3"))` returns rows for any listed actor.
- In the same persistence test, compose `byActors(...)` with resource and
  time-range predicates to prove it still combines with AND.

Verification after commit:

- `rtk ./gradlew test`
- `rtk ./gradlew integrationTest`

## Safe Commit 3 — Controller Wiring

Wire actor-list parsing into the HTTP GET endpoint.

Implementation:

- Inject/use `ActorFilterParser` in `AuditEventController.search(...)`.
- Parse the optional raw `actor` query parameter before constructing
  `SearchQuery`.
- Pass the canonical actor-id list into `SearchQuery`.
- Preserve current behavior for `resource`, `from`, `to`, `pageToken`, and
  `size`.
- Preserve the position-only cursor design; do not embed filters in the token.

Tests:

- Controller integration tests for happy paths:
  - `GET /audit-events?actor=a1`,
  - `GET /audit-events?actor=a1,a2,a3`,
  - `GET /audit-events?actor=<10 actor ids>`.
- Assert each happy path returns only matching actors and excludes non-matching
  actors.
- Controller integration test for trimming:
  - `GET /audit-events?actor=%20a1%20,%20a2%20`.
- Controller integration test for duplicates:
  - `GET /audit-events?actor=a1,a1,a2` behaves like `actor=a1,a2`.
- Controller integration tests for validation:
  - empty actor value/list entries return `400` with `field == "actor"`,
  - eleven raw actor entries return `400` with `field == "actor"`.

Verification after commit:

- `rtk ./gradlew test`
- `rtk ./gradlew integrationTest`

## Safe Commit 4 — Mixed-Filter Keyset Pagination Regression

Add the security analyst pagination regression for actor-list filter identity.

Implementation:

- No new production behavior should be needed after commit 3.
- Add an integration test that pages over a mixed filter:
  `actor=a1,a2&resource=project:42`.
- Request page 1 with one actor order, then request page 2 with the returned
  `pageToken` and reordered actors, such as `actor=a2,a1`.
- Include seeded rows that should be excluded by actor and seeded rows that
  should be excluded by resource.

Test assertions:

- Combined page results include every originally matching row exactly once.
- No duplicate IDs across pages.
- No matching row is skipped.
- Rows outside the actor set are excluded.
- Rows outside the resource filter are excluded.
- Reordering the same actor set does not create a pagination gap or overlap.

Verification after commit:

- `rtk ./gradlew integrationTest`

## Safe Commit 5 — README and Final Verification

Document the public query contract and run the full required checks.

Implementation:

- Update README `GET /audit-events` docs to show:
  - comma-separated actor filters,
  - maximum ten raw actor entries,
  - `400` for empty actor values/list entries,
  - `400` for eleven or more raw actor entries,
  - `pageToken` continuation with repeated filters.
- Keep the README focused on user/operator-facing behavior only.

Final verification:

- `rtk ./gradlew test`
- `rtk ./gradlew integrationTest`
- `rtk ./gradlew check`
- `rtk ./gradlew build`

Post-verification spec bookkeeping:

- Append the T08 execution result to `../tasks.md` after the implementation is
  complete.
- Include README status and verification results in the PR description.

## Assumptions and Constraints

- T08 remains one task, one branch, and one PR: `query-api/t08-actor-list-filter`.
- The split is by safe commits within that PR, not separate task PRs.
- No T08 migration is required; `design.md` justifies the existing
  `idx_audit_events_actor_time (actor, occurred_at DESC, id DESC)` index.
- Actor filtering uses a single query-level OR/`IN` predicate, not one query per
  actor.
- Matching is exact and case-sensitive.
- Repeated query parameters like `?actor=a1&actor=a2` remain out of scope.
- The cursor remains position-only and does not embed filter state.
