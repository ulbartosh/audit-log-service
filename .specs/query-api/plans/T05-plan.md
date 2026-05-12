# T05 — Add `PageTokenCodec` and `InvalidPageTokenException` (unused)

## Context

T05 is the fifth of seven tasks in the [`query-api`](../) spec. It introduces the wire-format codec for the keyset pagination cursor and the error path that fires when a client sends a malformed `pageToken`:

- `controller/PageTokenCodec` — Spring bean that encodes a `domain.Cursor` into the opaque base64-url JSON `{"v":1,"occurredAt":…,"id":…}` and decodes it back, throwing `InvalidPageTokenException` on any malformed input.
- `controller/InvalidPageTokenException` — runtime exception used by the codec; carries the field name `"pageToken"` so the existing `{ errors: [{ field, message }] }` envelope can name the offending parameter.
- `controller/GlobalExceptionHandler` — gains one `@ExceptionHandler` that maps `InvalidPageTokenException` to `400 Bad Request` with `errors[0].field == "pageToken"`.

Both new types are introduced **unused by production code** in this PR. They are picked up by T07 when `AuditEventController.search(...)` switches from `page/size` to `pageToken/size`. Landing them in isolation keeps T07 focused on the search-flow wiring and keeps this PR independently reviewable / revertible (`AGENTS.md` § PR invariant #2).

Base64 stays inside `controller/` per the domain-purity invariant (design.md § AGENTS.md alignment) — the `domain.Cursor` record knows nothing about base64 or JSON.

**Parallel-execution note.** T05 depends on **T03** (needs `domain.Cursor`). T03 is in flight in parallel; the branching plan below assumes T03 is merged into `main` before T05's branch is created. If T03 has not landed when T05 is ready, T05 branches off T03's tip (and rebases onto `main` after T03 merges). T05 has **no file overlap** with the in-flight T02 (`AuditEvent` / `NewAuditEvent`) — different files, different package areas.

References:
- Task definition: [`../tasks.md` § 05](../tasks.md#05--add-pagetokencodec-and-invalidpagetokenexception-unused)
- Design: [`../design.md` § Pagination strategy and cursor format → Cursor / Malformed token](../design.md#pagination-strategy-and-cursor-format), [§ Layer integration → controller/](../design.md#layer-integration)
- Requirements: no AC satisfied directly in this step; preparation for `analyst/pagination`, `analyst/malformed-token` (both verified by T07).

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/controller/PageTokenCodec.java` | **Add.** New `@Component` bean. | Encode/decode `Cursor` ↔ opaque base64-url JSON. |
| `src/main/java/com/training/bartosh/auditlog/controller/InvalidPageTokenException.java` | **Add.** New `RuntimeException`. | Signal malformed token; carries `field = "pageToken"`. |
| `src/main/java/com/training/bartosh/auditlog/controller/GlobalExceptionHandler.java` | **Edit.** Add one `@ExceptionHandler`. | Map `InvalidPageTokenException` → `400` with the existing error envelope. |
| `src/test/java/com/training/bartosh/auditlog/controller/PageTokenCodecTest.java` | **Add.** Unit tests (no Spring). | Cover round-trip + every documented decoder-rejection branch. |
| `src/test/java/com/training/bartosh/auditlog/controller/GlobalExceptionHandlerTest.java` | **Edit.** Add one test method. | Assert the new handler returns the correct envelope. |
| `src/test/java/com/training/bartosh/auditlog/controller/PageTokenSliceController.java` | **Add.** Test-only `@RestController`. | Throwable endpoint used only by the slice test. |
| `src/test/java/com/training/bartosh/auditlog/controller/PageTokenErrorMappingSliceTest.java` | **Add.** `@WebMvcTest` slice test. | DoD-required end-to-end verification that Spring's exception resolution wires `InvalidPageTokenException` → 400 envelope. |

No README change — no user/operator-visible behavior change yet (T07 owns that).

## Design decisions (locked)

- **ObjectMapper.** `PageTokenCodec` constructor-injects the Spring-Boot-configured `ObjectMapper` bean. Keeps Jackson modules (notably `JavaTimeModule` for `Instant`) consistent with the rest of the JSON layer; no duplicate ObjectMapper lifecycle.
- **Internal token shape.** A `private static record Token(int v, Instant occurredAt, UUID id)` inside `PageTokenCodec` is the marshalling DTO. Jackson handles `Instant` / `UUID` natively via `JavaTimeModule`. Single DTO used for both encode (build → write) and decode (read → validate).
- **Base64 flavor.** `Base64.getUrlEncoder().withoutPadding()` for encode; `Base64.getUrlDecoder()` for decode (tolerates absence of `=` padding). Matches design.md "base64-url" and the DoD assertion that output has no `+` / `/` / `=` characters.
- **Token version.** Hard-coded constant `private static final int VERSION = 1;`. Decode rejects any other value with `InvalidPageTokenException`.
- **Exception message.** Single uniform message `"Invalid page token"` regardless of root cause. The root cause is preserved as `Throwable cause` for log forensics but is not exposed to the response body. The DoD only asserts `field == "pageToken"`; not leaking parse internals to clients is the conservative default.
- **Slice test scope.** A test-only `@RestController` (`PageTokenSliceController`) is added to the test source set; `@WebMvcTest(controllers = PageTokenSliceController.class)` builds a minimal Spring web context that includes `@ControllerAdvice` beans by default. This verifies the *real* Spring exception-resolution path picks up `GlobalExceptionHandler`, without dragging in the persistence layer.

## `InvalidPageTokenException.java` — exact body

```java
package com.training.bartosh.auditlog.controller;

public class InvalidPageTokenException extends RuntimeException {

  private static final String FIELD = "pageToken";

  public InvalidPageTokenException(String message) {
    super(message);
  }

  public InvalidPageTokenException(String message, Throwable cause) {
    super(message, cause);
  }

  public String field() {
    return FIELD;
  }
}
```

## `PageTokenCodec.java` — exact body

```java
package com.training.bartosh.auditlog.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.bartosh.auditlog.domain.Cursor;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PageTokenCodec {

  private static final int VERSION = 1;
  private static final String INVALID = "Invalid page token";

  private final ObjectMapper mapper;

  public PageTokenCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String encode(Cursor cursor) {
    try {
      byte[] json =
          mapper.writeValueAsBytes(new Token(VERSION, cursor.occurredAt(), cursor.id()));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to encode page token", e);
    }
  }

  public Cursor decode(String token) {
    byte[] json;
    try {
      json = Base64.getUrlDecoder().decode(token);
    } catch (IllegalArgumentException e) {
      throw new InvalidPageTokenException(INVALID, e);
    }
    Token parsed;
    try {
      parsed = mapper.readValue(json, Token.class);
    } catch (JsonProcessingException e) {
      throw new InvalidPageTokenException(INVALID, e);
    }
    if (parsed == null
        || parsed.v() != VERSION
        || parsed.occurredAt() == null
        || parsed.id() == null) {
      throw new InvalidPageTokenException(INVALID);
    }
    return new Cursor(parsed.occurredAt(), parsed.id());
  }

  private record Token(int v, Instant occurredAt, UUID id) {}
}
```

Notes:
- `Cursor`'s compact ctor already rejects null `occurredAt` / `id`; the codec's explicit null checks fire *before* construction so the failure is `InvalidPageTokenException`, not `IllegalArgumentException`.
- `Base64.getUrlDecoder()` rejects non-base64-url bytes with `IllegalArgumentException`. Catching it and re-throwing as `InvalidPageTokenException` keeps the error envelope consistent.
- Missing-field cases land in the explicit `null` / `v != 0` checks (`int v` defaults to `0`, which != `VERSION`, so it's rejected).
- Non-ISO-8601 `occurredAt` and non-UUID `id` fall into the `JsonProcessingException` branch (Jackson type coercion failures).

## `GlobalExceptionHandler.java` — edit

Add one method below the existing `handleIllegalArgument`:

```java
@ExceptionHandler(InvalidPageTokenException.class)
public ResponseEntity<Map<String, Object>> handleInvalidPageToken(InvalidPageTokenException ex) {
  return ResponseEntity.badRequest()
      .body(Map.of("errors", List.of(Map.of("field", ex.field(), "message", ex.getMessage()))));
}
```

No imports change (`Map`, `List`, `ResponseEntity`, `ExceptionHandler` are already imported).

## Tests

### `PageTokenCodecTest.java`

Pure POJO unit test (no Spring). Build a real `ObjectMapper` with `JavaTimeModule` so `Instant` round-trips correctly:

```java
ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
PageTokenCodec codec = new PageTokenCodec(mapper);
```

Test methods:

| Test | Body |
|---|---|
| `roundTripsCursor` | Encode a representative `Cursor`, decode the result, assert equality on `occurredAt` and `id`. |
| `encoderOutputIsBase64UrlWithoutPadding` | Encode a cursor, assert the output contains no `+`, no `/`, no `=`. (Decoder still accepts both flavors; this asserts our encoder's flavor.) |
| `decodeRejectsNonBase64` | `codec.decode("!!!not base64!!!")` → `InvalidPageTokenException`. |
| `decodeRejectsMalformedJson` | Encode `"{not json"` as base64-url, decode → `InvalidPageTokenException`. |
| `decodeRejectsMissingFields` | Encode `{"v":1}` (no `occurredAt`/`id`) → `InvalidPageTokenException`. |
| `decodeRejectsUnsupportedVersion` | Encode `{"v":2,"occurredAt":…,"id":…}` → `InvalidPageTokenException`. |
| `decodeRejectsNonIso8601OccurredAt` | Encode `{"v":1,"occurredAt":"not-a-date","id":"<uuid>"}` → `InvalidPageTokenException`. |
| `decodeRejectsNonUuidId` | Encode `{"v":1,"occurredAt":"<iso>","id":"not-a-uuid"}` → `InvalidPageTokenException`. |

Each rejection test exercises exactly one DoD-listed branch in the decoder; together they cover every documented malformed-input class.

### `GlobalExceptionHandlerTest.java` — extension

Add one method alongside `illegalArgumentMapsToBadRequestErrorShape`:

```java
@Test
void invalidPageTokenMapsToBadRequestWithFieldPageToken() {
  ResponseEntity<Map<String, Object>> response =
      handler.handleInvalidPageToken(new InvalidPageTokenException("Invalid page token"));

  assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  assertEquals(
      Map.of(
          "errors",
          List.of(Map.of("field", "pageToken", "message", "Invalid page token"))),
      response.getBody());
}
```

### `PageTokenSliceController.java` (test source set only)

```java
package com.training.bartosh.auditlog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PageTokenSliceController {

  @GetMapping("/__test/throw-page-token")
  public void throwIt() {
    throw new InvalidPageTokenException("Invalid page token");
  }
}
```

Package-private; visible only inside the test source set. Path is `/__test/...` to make it obvious in any accidental log that this is a test fixture.

### `PageTokenErrorMappingSliceTest.java`

```java
package com.training.bartosh.auditlog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PageTokenSliceController.class)
class PageTokenErrorMappingSliceTest {

  @Autowired private MockMvc mvc;

  @Test
  void invalidPageTokenRendersAs400WithFieldPageToken() throws Exception {
    mvc.perform(get("/__test/throw-page-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("pageToken"))
        .andExpect(jsonPath("$.errors[0].message").value("Invalid page token"));
  }
}
```

`@WebMvcTest` auto-includes `@ControllerAdvice` beans, so `GlobalExceptionHandler` is picked up without explicit `@Import`.

## Definition of Done

Mirrors `tasks.md` T05 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew test --tests "*PageTokenCodecTest*" --tests "*GlobalExceptionHandlerTest*" --tests "*PageTokenErrorMappingSliceTest*"` passes — including the eight new codec tests, the new handler test, and the slice test.
- [ ] Encoder output verified base64-url with no padding (asserted in `encoderOutputIsBase64UrlWithoutPadding`).
- [ ] Every documented decoder-rejection branch has a named test (eight cases above) and the corresponding code path throws `InvalidPageTokenException`.
- [ ] Slice test demonstrates the *real* MVC exception-resolution path: `InvalidPageTokenException` thrown by a controller renders as 400 with `errors[0].field == "pageToken"`.
- [ ] ArchUnit `controllerDoesNotAccessPersistence` and the four layered rules still pass — no new persistence imports from `controller/`. (No `domain/` purity rule is touched; the only domain reference is to `Cursor`.)
- [ ] No production caller invokes `PageTokenCodec` or throws `InvalidPageTokenException` outside the test source set — verified by `rg 'PageTokenCodec|InvalidPageTokenException' src/main/java`. Expected matches: only the three new/edited production files.
- [ ] No new spotless or compiler warnings; no `System.out.println`; no `TODO` without an issue reference.

## Verification — end-to-end manual

After local commit, before pushing the PR branch:

```bash
./gradlew clean build
./gradlew test --tests "*PageTokenCodecTest*" \
              --tests "*GlobalExceptionHandlerTest*" \
              --tests "*PageTokenErrorMappingSliceTest*" --info
rg --files-with-matches 'PageTokenCodec|InvalidPageTokenException' src/main/java
```

Expected:
- First two commands: green.
- Third command: lists exactly `PageTokenCodec.java`, `InvalidPageTokenException.java`, and `GlobalExceptionHandler.java`.

## Out of scope (deferred to later tasks)

- Wiring `PageTokenCodec` into `AuditEventController.search(...)` (replace `page`/`size` with `pageToken`/`size`) — T07.
- Composing `domain.Cursor` into a JPA `Specification` via `AuditEventSpecifications.afterCursor(...)` — T04.
- Removing `PagedResponse<T>` and switching to `KeysetPageResponse<T>` — T06 / T07.

`PageTokenCodec` and `InvalidPageTokenException` are unreferenced by production code after this PR — verified by the `rg` check in the DoD. The only exercising paths are unit tests and the slice test's test-only controller.

## Open questions

None.

- ObjectMapper source: **inject Spring's** (consistent JSON config).
- Token JSON shape: **private record** (clean typing; one DTO for encode and decode).
- Slice test: **test-only controller** (clean isolation; exercises real Spring exception resolution).

## Branch & PR

- **Branch:** `query-api/t05-page-token-codec` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3). T05 depends on T03 — if T03 has not yet merged to `main`, branch from T03's tip and rebase onto `main` after T03 lands.
- **PR title:** `feat(query-api): add PageTokenCodec and InvalidPageTokenException`
- **PR description:**
  - Maps DoD to evidence (test names; `./gradlew build` link).
  - Notes that both types are intentionally unused; cites T07 as the consumer.
  - States explicitly that no README update is needed (no user/operator-visible change) — `AGENTS.md` § PR invariant #4.
  - Confirms ACs: none satisfied directly in this PR (preparation step); the spec coverage table in `tasks.md` attributes the `analyst/malformed-token` AC to T07.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 05 in `tasks.md` (per the `_(append after merge)_` placeholder).
