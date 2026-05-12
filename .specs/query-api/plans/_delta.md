# Query API plans/spec delta

Compared:

- `requirements.md`
- `design.md`
- `plans/T01-plan.md` through `plans/T07-plan.md`

## Material deltas

### 1. `actor.type` defaulting layer differs

**Spec:** `design.md` says the new POST `actor.type` field is optional and defaults to `USER`, with defaulting "done in the service, not the DTO."

**Plan:** `T02-plan.md` explicitly resolves this to defaulting in `AuditEventController.create(...)` while mapping `ActorRequest` to the domain `Actor`. The plan explains this as "controller boundary" defaulting because the service cannot import controller DTOs under the layered architecture rules.

**Impact:** Wire behavior is the same, but ownership differs from the design wording. Resolve by either updating `design.md` to say "defaulting at the application/controller boundary, not in the DTO" or changing the T02 plan to introduce a service-level input shape/factory where the default can live without importing controller DTOs.

### 2. `size <= 0` behavior differs

**Spec:** `design.md` validation rules say `size` is an integer `>= 1`, defaults to `50`, and is silently capped at `500`.

**Plan:** `T07-plan.md` clamps the HTTP request value with `Math.min(Math.max(size, 1), MAX_PAGE_SIZE)`. That means `size=0` or `size=-5` becomes `1` and returns `200 OK`, rather than being rejected.

**Impact:** Clients sending invalid lower-bound sizes receive a successful response instead of the spec-implied `400 Bad Request`. Resolve by either changing the controller plan to reject `size < 1`, or changing `design.md` to explicitly say lower-bound sizes are also silently clamped to `1`.

## Coverage gaps

### 3. GET blank `resource` is not explicitly tested

**Spec:** `design.md` says blank `actor` and blank `resource` query parameters return `400 Bad Request`.

**Plan:** `T07-plan.md` implements both checks in `SearchQuery`, but the listed controller ITs only include `getRejectsBlankActor`. There is no matching `getRejectsBlankResource` test.

**Impact:** The planned implementation appears to satisfy the behavior, but the DoD does not explicitly verify the `resource` half of the spec rule.

### 4. POST blank `resource.type` is not explicitly tested

**Spec:** `design.md` says `resource.type` is optional, but if present it must be non-blank.

**Plan:** `T02-plan.md` routes request data through the domain `Resource` compact constructor, which rejects blank `type`, but the listed integration tests cover blank `actor.id` and blank `resource.id` only. There is no explicit blank `resource.type` test.

**Impact:** The behavior is likely enforced indirectly, but the DoD does not explicitly lock the validation rule from the design.

## Non-deltas noted

- The step plans intentionally leave intermediate states that do not yet satisfy the final `GET /audit-events` design, especially T02 keeping offset pagination until T07. This is sequencing, not a final spec mismatch.
- T04 notes that it can compile without T03 even though `tasks.md` lists T04 after T03. That is a task-dependency refinement, not a difference from `requirements.md` or `design.md`.
