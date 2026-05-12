# T02 — Structured `actor` / `resource` and `payload` end-to-end

## Context

T02 is the second of seven tasks in the [`query-api`](..) spec. It is a cross-layer breaking change: `actor` and `resource` move from flat `String` to structured records `{ id, type }`, and a new `JsonNode payload` field appears alongside `context`. Both the GET response and the POST request adopt the new shape.

Why this PR can't be split smaller without throwaway adapters: changing `AuditEvent.actor` from `String` to `Actor` breaks the mapper, the controller's response factory, and the service tests in the same compile. Each commit must build green (`AGENTS.md` § Build health #1), so the shape change ships as one cohesive PR.

T01 has already landed the V4 schema (the `actor_type` / `resource_type` / `payload` columns exist with defaults). T02 wires the application to those columns.

References:
- Task definition: [`../tasks.md` § 02](../tasks.md#02--structured-actor--resource-and-payload-end-to-end)
- Design: [`../design.md` § API contract](../design.md#api-contract), [§ Changes to existing `POST /audit-events`](../design.md#changes-to-existing-post-audit-events), [§ Layer integration](../design.md#layer-integration)
- ACs implemented: `compliance/actor-structured`, `compliance/resource-structured`, `sre/payload-present`, `sre/payload-absent`; re-asserted under new shape: `sre/context-present`, `sre/context-absent`.

**Resolved during planning:** `actor.type` defaulting to `USER` happens at the **controller boundary** (`AuditEventController.create(...)` when constructing the domain `Actor` from `ActorRequest`). Design.md's "in the service, not the DTO" phrasing is interpreted as "not as a field-initializer in `ActorRequest`"; the service can't import `controller/dto/ActorRequest` per the layered-architecture ArchUnit rule, so the controller is the cleanest layer for the mapping. The domain `Actor` compact ctor stays strict (rejects null `type`), matching design.md's stated invariant.

## Files to add / modify

| Path | Change | Why |
|---|---|---|
| `src/main/java/com/training/bartosh/auditlog/domain/ActorType.java` | **Add.** Enum with single value `USER`. | Design.md § Layer integration → domain/. |
| `src/main/java/com/training/bartosh/auditlog/domain/Actor.java` | **Add.** Record `Actor(String id, ActorType type)` with strict compact ctor. | Design.md § Layer integration → domain/. |
| `src/main/java/com/training/bartosh/auditlog/domain/Resource.java` | **Add.** Record `Resource(String id, String type)` with compact ctor (`id` non-blank, `type` nullable but non-blank when set). | Design.md § Layer integration → domain/. |
| `src/main/java/com/training/bartosh/auditlog/domain/AuditEvent.java` | **Modify.** `String actor` → `Actor actor`; `String resource` → `Resource resource`; add `JsonNode payload` (nullable); update compact ctor. | Design.md § Layer integration → domain/. |
| `src/main/java/com/training/bartosh/auditlog/domain/NewAuditEvent.java` | **Modify.** Same shape change as `AuditEvent`; outcome defaulting behavior preserved. | Design.md § Layer integration → domain/. |
| `src/main/java/com/training/bartosh/auditlog/persistence/AuditEventEntity.java` | **Modify.** Add fields `actorType`, `resourceType`, `payload` with annotations matching design. Constructor + getters extended. | Design.md § Layer integration → persistence/. |
| `src/main/java/com/training/bartosh/auditlog/persistence/AuditEventArchiveEntity.java` | **Modify.** Mirror the three field additions; constructor + getters extended. | Design.md § Layer integration → persistence/ (archive mirrors main). |
| `src/main/java/com/training/bartosh/auditlog/persistence/AuditEventMapper.java` | **Modify.** Decompose `Actor`/`Resource` on write; compose on read; propagate `payload`. | Design.md § Layer integration → persistence/ → `AuditEventMapper`. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/ActorRequest.java` | **Add.** `record ActorRequest(@NotBlank String id, ActorType type)`. | Design.md § Layer integration → controller/. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/ActorResponse.java` | **Add.** `record ActorResponse(String id, ActorType type)`. | Design.md § Layer integration → controller/. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/ResourceRequest.java` | **Add.** `record ResourceRequest(@NotBlank String id, String type)`. | Design.md § Layer integration → controller/. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/ResourceResponse.java` | **Add.** `record ResourceResponse(String id, String type)`. | Design.md § Layer integration → controller/. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/CreateAuditEventRequest.java` | **Modify.** Swap `@NotBlank String actor` for `@Valid @NotNull ActorRequest actor`; swap `String resource` for `@Valid ResourceRequest resource`; add `JsonNode payload`. | Design.md § Changes to existing `POST /audit-events`. |
| `src/main/java/com/training/bartosh/auditlog/controller/dto/AuditEventResponse.java` | **Modify.** Swap `String actor/resource` for `ActorResponse/ResourceResponse`; add `JsonNode payload` (inherits `@JsonInclude(NON_NULL)` from class). Update `from(AuditEvent)` factory. | Design.md § API contract. |
| `src/main/java/com/training/bartosh/auditlog/controller/AuditEventController.java` | **Modify.** `create(...)` constructs domain `Actor`/`Resource` from request DTOs, applying `USER` default for `actor.type` when null. `search(...)` unchanged. | Controller-boundary defaulting (per planning decision). |
| `src/test/java/com/training/bartosh/auditlog/domain/AuditEventTest.java` | **Extend.** New `assertThrows` cases for `Actor` compact ctor (null `id`, blank `id`, null `type`) and `Resource` (null `id`, blank `id`, blank `type` when non-null). Update existing `AuditEvent` and `NewAuditEvent` instantiations to new signatures. | Match existing one-test-per-invariant pattern. |
| `src/test/java/com/training/bartosh/auditlog/persistence/AuditEventArchiveEntityTest.java` | **Extend.** Constructor call gains `actorType`, `resourceType`, `payload`; assertions extended. | Existing constructor-and-getters snapshot pattern. |
| `src/test/java/com/training/bartosh/auditlog/service/AuditEventServiceTest.java` | **Modify.** Four call sites of `new NewAuditEvent(String, ...)` switch to `new NewAuditEvent(new Actor(...), ...)` plus new `payload` arg. Persisted-entity assertions switch to `getActor()` (the column string), keep `getActor().equals("u1")` semantics. | Signature change ripples through the four tests. |
| `src/integrationTest/java/com/training/bartosh/auditlog/controller/AuditEventControllerIT.java` | **Modify + extend.** Rewrite `seed(...)` helper to produce nested JSON; update existing assertions (`$.actor` → `$.actor.id`, etc.); add tests for structured round-trip, `actor.type` defaulting to `USER`, `payload` round-trip, and `payload` absence from JSON when null. | DoD requires explicit IT coverage of the new shape. |
| `README.md` | **Modify.** Update POST/GET JSON examples, curl smoke test, event-schema table to include structured `actor`/`resource` and `payload`. Note breaking change in the API section. | `AGENTS.md` § PR invariant #4. |

ArchUnit boundary tests (`src/test/java/.../architecture/ArchitectureTest.java`) are not edited — they continue to enforce that `Actor`/`Resource`/`ActorType` in `domain/` carry no Spring/JPA imports. Verified mentally: `domain.ActorType` is a plain enum, `Actor`/`Resource` records use only `String`/built-ins.

## Domain layer — exact shapes

### `domain/ActorType.java`

```java
package com.training.bartosh.auditlog.domain;

public enum ActorType {
  USER
}
```

### `domain/Actor.java`

```java
package com.training.bartosh.auditlog.domain;

public record Actor(String id, ActorType type) {

  public Actor {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("actor id is required");
    }
    if (type == null) {
      throw new IllegalArgumentException("actor type is required");
    }
  }
}
```

### `domain/Resource.java`

```java
package com.training.bartosh.auditlog.domain;

public record Resource(String id, String type) {

  public Resource {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("resource id is required");
    }
    if (type != null && type.isBlank()) {
      throw new IllegalArgumentException("resource type must be non-blank when present");
    }
  }
}
```

### `domain/AuditEvent.java` and `domain/NewAuditEvent.java` — modifications

- `AuditEvent`: field positions for `actor` and `resource` keep their order; types become `Actor` and `Resource`. Add `JsonNode payload` at the end (after `context`). Compact ctor: drop `actor.isBlank` (delegated to `Actor`); `Resource` validation is in its own compact ctor. `payload` is unvalidated (nullable).
- `NewAuditEvent`: same shape change. Outcome defaulting in compact ctor preserved. Actor non-null check stays (matching the design.md note that `Actor` itself enforces non-blank `id`).

## Persistence layer — exact additions

### `AuditEventEntity` and `AuditEventArchiveEntity`

Append three fields after the existing `context` field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "actor_type", nullable = false)
private ActorType actorType;

@Column(name = "resource_type")
private String resourceType;

@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private JsonNode payload;
```

Extend the constructor signature to accept the three new fields (positional, appended after `context`). Add three getters. Both entity classes mirror this addition. No setters.

### `AuditEventMapper`

```java
public static AuditEvent toDomain(AuditEventEntity entity) {
  Actor actor = new Actor(entity.getActor(), entity.getActorType());
  Resource resource =
      entity.getResource() == null ? null : new Resource(entity.getResource(), entity.getResourceType());
  return new AuditEvent(
      entity.getId(),
      entity.getOccurredAt(),
      actor,
      entity.getAction(),
      resource,
      entity.getOutcome(),
      entity.getContext(),
      entity.getPayload());
}

public static AuditEventEntity toEntity(AuditEvent event) {
  return new AuditEventEntity(
      event.id(),
      event.occurredAt(),
      event.actor().id(),
      event.actor().type(),
      event.action(),
      event.resource() == null ? null : event.resource().id(),
      event.resource() == null ? null : event.resource().type(),
      event.outcome(),
      event.context(),
      event.payload());
}
```

`AuditEventSpecifications` is **not modified** in T02 — the `byActor(String)` / `byResource(String)` helpers continue to match the existing `actor` / `resource` string columns. The new `actor_type` / `resource_type` columns are not filterable in this iteration (per design.md § Layer integration → persistence/). `afterCursor` is T04.

`AuditEventEntity_` regenerates automatically via the Hibernate annotation processor — no manual edits.

## Controller layer

### New nested DTOs

```java
// dto/ActorRequest.java
public record ActorRequest(@NotBlank String id, ActorType type) {}

// dto/ActorResponse.java
public record ActorResponse(String id, ActorType type) {}

// dto/ResourceRequest.java
public record ResourceRequest(@NotBlank String id, String type) {}

// dto/ResourceResponse.java — per-field @JsonInclude(NON_NULL) so resource.type is omitted when null
public record ResourceResponse(String id, @JsonInclude(JsonInclude.Include.NON_NULL) String type) {}
```

Design.md § API contract → response specifies: `resource.type` is omitted per-event when null. That requires per-field `@JsonInclude(NON_NULL)` on `ResourceResponse.type` specifically (the parent `@JsonInclude(NON_NULL)` on `AuditEventResponse` would only omit the entire `resource` field, not its sub-fields). `ActorResponse.type` is never null (domain invariant), so no per-field annotation is needed there.

### `CreateAuditEventRequest`

```java
public record CreateAuditEventRequest(
    @Valid @NotNull ActorRequest actor,
    @NotBlank String action,
    @Valid ResourceRequest resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {}
```

Field order: `actor, action, resource, outcome, context, payload` (existing order preserved; `payload` appended).

### `AuditEventResponse`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventResponse(
    UUID id,
    Instant occurredAt,
    ActorResponse actor,
    String action,
    ResourceResponse resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {

  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.occurredAt(),
        new ActorResponse(event.actor().id(), event.actor().type()),
        event.action(),
        event.resource() == null ? null : new ResourceResponse(event.resource().id(), event.resource().type()),
        event.outcome(),
        event.context(),
        event.payload());
  }
}
```

`payload` inherits the class-level `@JsonInclude(NON_NULL)` — no per-field annotation needed (matches the existing treatment of `context`).

### `AuditEventController.create(...)`

The only behavioral change. Construct domain `Actor` and `Resource` from the request DTOs, applying `USER` as the default for `actor.type`:

```java
@PostMapping
public ResponseEntity<AuditEventResponse> create(@Valid @RequestBody CreateAuditEventRequest req) {
  Actor actor = new Actor(
      req.actor().id(),
      req.actor().type() != null ? req.actor().type() : ActorType.USER);
  Resource resource = req.resource() == null
      ? null
      : new Resource(req.resource().id(), req.resource().type());
  NewAuditEvent input =
      new NewAuditEvent(actor, req.action(), resource, req.outcome(), req.context(), req.payload());
  AuditEvent event = service.record(input);
  URI location =
      ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(event.id()).toUri();
  return ResponseEntity.created(location).body(AuditEventResponse.from(event));
}
```

`search(...)` is **untouched** — it still returns `PagedResponse<AuditEventResponse>` with offset paging; T07 swaps it.

## Test updates — concrete

### `domain/AuditEventTest.java`

Existing tests use 7-arg constructors like `new AuditEvent(ID, NOW, "u1", "user.login", null, Outcome.SUCCESS, null)`. Each call site updates to the new 8-arg shape: `new AuditEvent(ID, NOW, new Actor("u1", ActorType.USER), "user.login", null, Outcome.SUCCESS, null, null)`.

Add new test methods following the existing one-test-per-invariant pattern:
- `actorRejectsNullId` / `actorRejectsBlankId` / `actorRejectsNullType` — direct `assertThrows` on `new Actor(...)`.
- `resourceRejectsNullId` / `resourceRejectsBlankId` / `resourceRejectsBlankType` — direct `assertThrows` on `new Resource(...)`.
- `resourceAcceptsNullType` — happy-path constructor.
- `auditEventCarriesPayload` — happy-path assertion that `payload` round-trips through the constructor.
- `newAuditEventCarriesPayload` — likewise on `NewAuditEvent`.

Existing tests `rejectsNullActor` and `rejectsBlankActor` on `AuditEvent` adjust: the rejection now bubbles up from `Actor`'s compact ctor when constructing the `Actor` literal in the test. Recast the test to construct via the `Actor` ctor directly and verify the right `IllegalArgumentException` location, or remove the test and rely on the new `actorRejectsNullId` cases — choose the simpler path (remove the duplicates, keep the Actor-level ones).

### `persistence/AuditEventArchiveEntityTest.java`

Constructor call grows from 8 args to 11 args (adds `actorType`, `resourceType`, `payload`). Add three assertions on the new getters.

### `service/AuditEventServiceTest.java`

Four call sites of `new NewAuditEvent("u1", ...)` switch to `new NewAuditEvent(new Actor("u1", ActorType.USER), ...)`. Add a `null` for the new `payload` arg.

Assertions on the persisted entity:
- `assertEquals("u1", persisted.getActor())` stays as-is — `getActor()` on the entity is still the column string `actor`, not the domain object.
- `assertEquals("project:42", persisted.getResource())` likewise.
- Optionally add `assertEquals(ActorType.USER, persisted.getActorType())` to lock in the new column behavior.

### `integrationTest/.../controller/AuditEventControllerIT.java`

**`seed(...)` helper rewrite.** Two overloads today produce flat JSON; rewrite both to produce nested:

```java
private void seed(String actor, String action, String outcome) throws Exception {
  seed(actor, action, outcome, null);
}

private void seed(String actor, String action, String outcome, String resourceId) throws Exception {
  String resourceFragment =
      resourceId == null ? "" : ",\"resource\":{\"id\":\"%s\"}".formatted(resourceId);
  String body = """
      {"actor":{"id":"%s"},"action":"%s","outcome":"%s"%s}
      """.formatted(actor, action, outcome, resourceFragment);
  mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isCreated());
}
```

**Existing test updates:**

- `postCreatesEvent` — request body becomes `{"actor":{"id":"u1","type":"USER"},"action":"user.login","resource":{"id":"project:42","type":"project"},"outcome":"SUCCESS"}`. Assertions become `jsonPath("$.actor.id", equalTo("u1"))`, `jsonPath("$.actor.type", equalTo("USER"))`, `jsonPath("$.resource.id", equalTo("project:42"))`, `jsonPath("$.resource.type", equalTo("project"))`.
- `postRejectsMissingActor` — request body `{"action":"user.login","outcome":"SUCCESS"}` still produces `400`; the error envelope has `errors[*].field` equal to `"actor"` (the `@NotNull` violation on the parent record field). Add an assertion.
- `postIgnoresClientSuppliedTimestamp` — request body switches to nested `actor`. Unchanged otherwise.
- `postDefaultsOutcomeToSuccessWhenOmitted` — request body switches to nested `actor`. Unchanged otherwise.
- `getFiltersByActor` — `seed(...)` calls are unchanged; assertions become `$.items[0].actor.id`.
- `getFiltersByResource` — likewise: `$.items[0].resource.id`.
- `getFiltersByTimeRange` — switches to nested `actor` via `seed`.
- `getRespectsPaginationLimits` — switches to nested `actor`; offset response shape (`size`, `total`) preserved.
- `nullableFieldsOmittedFromResponse` — assert `$.resource`, `$.context`, **and `$.payload`** all `.doesNotExist()`.

**New test methods (per DoD):**

- `postRoundTripsStructuredActorAndResourceAndPayload` — explicit assertion for `compliance/actor-structured`, `compliance/resource-structured`, `sre/payload-present`: post a body with all four fields populated, assert the response carries them back unchanged.
- `postDefaultsActorTypeToUser` — body has `{"actor":{"id":"u1"},"action":"…","outcome":"SUCCESS"}`; assert `$.actor.type == "USER"` on the response.
- `postRejectsActorIdBlank` — body has `{"actor":{"id":"  "},…}`; assert `400` and `errors[*].field == "actor.id"` (Spring flattens nested field errors per `GlobalExceptionHandler.handleMethodArgumentNotValid`).
- `postRejectsResourceIdBlankWhenResourcePresent` — body has `{"actor":{"id":"u1"},"resource":{"id":""},…}`; assert `400` and `errors[*].field == "resource.id"`.

## README updates

Sections to touch (`README.md`):

1. **POST `/audit-events` example JSON.** Replace `"actor": "alice"` with `"actor": { "id": "alice", "type": "USER" }`. Replace `"resource": "project:42"` with `"resource": { "id": "project:42", "type": "project" }`. Add `"payload": { … }` as an optional field.
2. **Curl smoke test.** Update inline JSON body to nested shape.
3. **Event schema table.** Update `actor` and `resource` rows to indicate structured objects; add a `payload` row. Note that `actor.type` defaults to `USER`, `resource.type` is free-form optional.
4. **Breaking-change call-out.** One line near the top of the API section: "**Breaking change as of this release:** `actor` and `resource` are structured objects on POST and GET. Existing flat-string callers must update."

GET pagination shape stays `page/size/total` in this PR — that swap is T07.

## Definition of Done

Mirrors `tasks.md` T02 DoD, made concrete:

- [ ] `./gradlew build` exits 0 (compile + test + integrationTest + spotlessCheck + jacoco verify ≥ 90% line).
- [ ] `./gradlew test --tests "*.domain.*"` passes — including new `Actor`/`Resource` compact-ctor cases and `payload` round-trips.
- [ ] `./gradlew test --tests "*AuditEventServiceTest"` passes — four updated tests green.
- [ ] `./gradlew integrationTest --tests "*AuditEventControllerIT"` passes — including: `postRoundTripsStructuredActorAndResourceAndPayload`, `postDefaultsActorTypeToUser`, `postRejectsActorIdBlank`, `postRejectsResourceIdBlankWhenResourcePresent`, updated `nullableFieldsOmittedFromResponse` (with `payload` absent assertion).
- [ ] `./gradlew test --tests "*ArchitectureTest"` passes — the four ArchUnit rules continue to hold (no Spring/JPA imports in `domain/`; controller doesn't import `persistence/`).
- [ ] No new compiler warnings; spotless clean; no `TODO` without an issue reference; no `System.out.println`.
- [ ] README's POST/GET examples, curl smoke test, and event-schema table reflect the new shape (verified by re-reading after edit).
- [ ] PR description maps each AC implemented in this step to the test that covers it (per `AGENTS.md` § PR invariant #5).

## Verification — end-to-end manual

```bash
./gradlew clean build
./gradlew integrationTest --tests "*AuditEventControllerIT" --info
```

Both green. If you want a manual round-trip via a running server:

```bash
./gradlew bootRun &
sleep 5
curl -i -X POST http://localhost:8080/audit-events \
  -H 'Content-Type: application/json' \
  -d '{"actor":{"id":"alice"},"action":"user.login","resource":{"id":"project:42","type":"project"},"outcome":"SUCCESS","payload":{"amount":100}}'
curl -s 'http://localhost:8080/audit-events?actor=alice' | jq
```

Expected: POST returns `201` with structured response; GET returns the event with `actor.type: "USER"` (defaulted), `resource.type: "project"`, and `payload: {"amount":100}`.

## Out of scope (deferred to later tasks)

- `Cursor` / `KeysetPage` domain types — T03.
- `afterCursor` specification helper — T04.
- `PageTokenCodec` and `InvalidPageTokenException` — T05.
- `KeysetPageResponse<T>` DTO — T06.
- Keyset wiring through service + controller; removal of `PagedResponse` — T07.
- Filtering by `actor.type` or `resource.type` (out of scope per `requirements.md`).

## Open questions

None remaining. The controller-boundary defaulting of `actor.type = USER` was resolved during planning.

## Branch & PR

- **Branch:** `query-api/t02-structured-shape` (per `AGENTS.md` § PR invariant #2).
- **Base:** `main`, fast-forwarded immediately before branching (per `AGENTS.md` § PR invariant #3).
- **PR title:** `feat(query-api): structured actor/resource and payload end-to-end (breaking)`
- **PR description:** Map each AC to its test. Call out the breaking change explicitly — POST and GET wire shapes change; consumers must update. Reference design.md sections for the rationale.
- **Execution result append:** when the PR merges, append a 1–3 line result to step 02 in `tasks.md`.
