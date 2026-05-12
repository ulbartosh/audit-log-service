# T06 — Add `KeysetPageResponse<T>` DTO (unused)

## Context

T06 is the sixth of seven tasks in the [`query-api`](../) spec. It introduces the controller-layer DTO that wraps a keyset page on the wire:

- `controller/dto/KeysetPageResponse<T>(List<T> items, String nextPageToken)` — annotated `@JsonInclude(NON_NULL)` so `nextPageToken` is omitted from the response body when the current page exhausts the result set.

The DTO is introduced **unused by production code** in this PR. It is picked up by T07 when `AuditEventController.search(...)` switches from returning `PagedResponse<AuditEventResponse>` to `KeysetPageResponse<AuditEventResponse>`. Landing it in isolation keeps T07 focused on the search-flow wiring and keeps this PR independently reviewable / revertible (`AGENTS.md` § PR invariant #2).

Coexistence with `PagedResponse<T>` is intentional: `PagedResponse` stays in the codebase until T07 swaps the search endpoint to keyset paging and deletes the old DTO. This PR does not modify or delete `PagedResponse.java`.

**Parallel-execution note.** The `tasks.md` dependency graph lists T06 as depending on T03, but the dependency is **logical, not code-level**: `KeysetPageResponse<T>` references only stdlib types (`List<T>`, `String`) and `com.fasterxml.jackson.annotation.JsonInclude` — no reference to `domain.Cursor` or `domain.KeysetPage`. T06 therefore **does not need T03 merged** to compile or to land; it can be developed and merged independently. It also has **no file overlap** with the in-flight T02 (`domain/AuditEvent.java`, `domain/NewAuditEvent.java`), T03 (new files in `domain/`), or T05 (new files in `controller/`, edit to `controller/GlobalExceptionHandler.java`).

References:
- Task definition: [`../tasks.md` § 06](../tasks.md#06--add-keysetpageresponset-dto-unused)
- Design: [`../design.md` § API contract → GET /audit-events response](../design.md#api-contract), [§ Layer integration → controller/](../design.md#layer-integration)
- Requirements: no AC satisfied directly in this step; preparation for `compliance/empty-result`, `analyst/beyond-end` (both verified by T07).

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/controller/dto/KeysetPageResponse.java` | **Add.** New generic record DTO. | Wire shape for keyset pages on `GET /audit-events`. |
| `src/test/java/com/training/bartosh/auditlog/controller/dto/KeysetPageResponseTest.java` | **Add.** New unit test class. | Verify `@JsonInclude(NON_NULL)` omits `nextPageToken` when null and includes it when non-null. |

Nothing else changes. `PagedResponse.java` stays untouched (deleted in T07). No README update — no user/operator-visible behavior change yet. JaCoCo's existing line-coverage check applies; the new record is 100% covered by the new tests (records auto-generate accessors/constructors that Jackson exercises).

## Design decisions (locked)

- **`@JsonInclude` placement.** Class-level `@JsonInclude(JsonInclude.Include.NON_NULL)` — matches the existing `AuditEventResponse.java` style exactly. Per-field annotation would also work but is inconsistent with the established repo pattern.
- **Test ObjectMapper.** Fresh `new ObjectMapper()` in the test. The repo has no Jackson customizations (no `Jackson2ObjectMapperBuilderCustomizer`, no custom `@Configuration` for `ObjectMapper`), so a fresh default is functionally identical to Spring's bean for this DTO's serialization. Staying POJO-light matches the existing `GlobalExceptionHandlerTest` precedent for controller-layer unit tests.
- **Absence assertion.** Parse the serialized JSON with `mapper.readTree(...)` and assert `!tree.has("nextPageToken")` — robust against key-ordering, whitespace, or any incidental JSON formatting difference. Distinguishes "absent" from "present-as-`null`" precisely, which is what the DoD requires.
- **Generic concretisation in tests.** Use `KeysetPageResponse<String>` for both test cases — simplest concrete element type; the JSON shape under test does not depend on element type. (Item-list serialization is incidental; the test targets the wrapper.)

## `KeysetPageResponse.java` — exact body

```java
package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeysetPageResponse<T>(List<T> items, String nextPageToken) {}
```

Notes:
- No compact ctor; this is a controller-layer wire DTO, not a domain invariant carrier. The validity of `items` / `nextPageToken` is the responsibility of the producer (`AuditEventController` in T07) — DTOs in this repo do not police invariants (cf. `PagedResponse.java`, `AuditEventResponse.java`).
- No `from(...)` factory either; T07 will construct the response inline from a `KeysetPage<AuditEventResponse>`. Adding a factory now without a caller is premature scope per `AGENTS.md` guidance ("don't design for hypothetical future requirements").

## Tests

### `KeysetPageResponseTest.java`

Pure POJO unit test (no Spring). Lives in the test source set under `controller/dto/` mirroring the production package.

```java
package com.training.bartosh.auditlog.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeysetPageResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omitsNextPageTokenWhenNull() throws Exception {
    KeysetPageResponse<String> response = new KeysetPageResponse<>(List.of("a", "b"), null);

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("items"));
    assertFalse(tree.has("nextPageToken"), "nextPageToken must be absent when null");
  }

  @Test
  void includesNextPageTokenWhenPresent() throws Exception {
    KeysetPageResponse<String> response =
        new KeysetPageResponse<>(List.of("a"), "opaque-token-string");

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("nextPageToken"));
    assertTrue(tree.get("nextPageToken").isTextual(), "nextPageToken must serialize as string");
    assertEquals("opaque-token-string", tree.get("nextPageToken").asText());
  }
}
```

These two tests cover both DoD bullets:
1. `omitsNextPageTokenWhenNull` — DoD: "asserts the `"nextPageToken"` key is absent from the JSON output (not present-as-`null`)".
2. `includesNextPageTokenWhenPresent` — DoD: "Unit test serializes a non-null `nextPageToken` and asserts it appears as a string field".

Each test fails loudly if the `@JsonInclude(NON_NULL)` annotation is removed or moved off the type — required signal per `tasks.md` § 06 DoD.

## Definition of Done

Mirrors `tasks.md` T06 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew test --tests "*KeysetPageResponseTest*"` passes — both new test methods green.
- [ ] `omitsNextPageTokenWhenNull` and `includesNextPageTokenWhenPresent` are present and exercise the documented behavior.
- [ ] ArchUnit `layeredDependencies` and `controllerDoesNotAccessPersistence` still pass — no new persistence imports from `controller/dto/`.
- [ ] No production caller references `KeysetPageResponse` — verified by `rg 'KeysetPageResponse' src/main/java`. Expected matches: only `KeysetPageResponse.java` itself.
- [ ] `PagedResponse.java` is unchanged — verified by `git diff main -- src/main/java/com/training/bartosh/auditlog/controller/dto/PagedResponse.java` returning empty.
- [ ] No new spotless or compiler warnings; no `System.out.println`; no `TODO` without an issue reference.

## Verification — end-to-end manual

After local commit, before pushing the PR branch:

```bash
./gradlew clean build
./gradlew test --tests "*KeysetPageResponseTest*" --info
rg --files-with-matches 'KeysetPageResponse' src/main/java
git diff main -- src/main/java/com/training/bartosh/auditlog/controller/dto/PagedResponse.java
```

Expected:
- First two commands: green.
- Third command: lists only `KeysetPageResponse.java` (confirms zero accidental production wiring).
- Fourth command: empty output (confirms `PagedResponse` is untouched).

## Out of scope (deferred to later tasks)

- Replacing `PagedResponse<T>` with `KeysetPageResponse<T>` in `AuditEventController.search(...)` — T07.
- Deleting `PagedResponse.java` once nothing references it — T07.
- Building `KeysetPageResponse` from a `KeysetPage<AuditEvent>` produced by the service — T07.

`KeysetPageResponse` is unreferenced by production code after this PR — verified by the `rg` check in the DoD.

## Open questions

None.

- `@JsonInclude` placement: **class-level** (matches `AuditEventResponse`).
- Test ObjectMapper: **fresh `new ObjectMapper()`** (no repo-wide Jackson customization to honor).
- Absence assertion: **`tree.has("nextPageToken")`** via `readTree` (distinguishes absent from present-as-null precisely).

## Branch & PR

- **Branch:** `query-api/t06-keyset-page-response` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3). T06 has **no code-level dependency** on T02 / T03 / T04 / T05; it can be branched, reviewed, and merged independently regardless of which sibling tasks are in flight.
- **PR title:** `feat(query-api): add KeysetPageResponse DTO`
- **PR description:**
  - Maps DoD to evidence (test names; `./gradlew build` link).
  - Notes the DTO is intentionally unused; cites T07 as the consumer.
  - States explicitly that `PagedResponse.java` is untouched (deletion is T07's responsibility).
  - States explicitly that no README update is needed (no user/operator-visible change) — `AGENTS.md` § PR invariant #4.
  - Confirms ACs: none satisfied directly in this PR (preparation step); the spec coverage table in `tasks.md` attributes `compliance/empty-result` and `analyst/beyond-end` to T07.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 06 in `tasks.md` (per the `_(append after merge)_` placeholder).
