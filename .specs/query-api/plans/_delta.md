# Query API plans/spec delta

Compared:

- `requirements.md`
- `design.md`
- `plans/T01-plan.md` through `plans/T07-plan.md`

## Status

The deltas previously recorded in this file have been resolved by clarifying the specification and tightening the task plans:

- `design.md` now says `actor.type` defaulting happens while mapping the request into the domain model, not as a DTO field initializer.
- `design.md` now distinguishes `size > 500` (silently capped) from `size < 1` and non-integer `size` (both `400`).
- `T07-plan.md` and `tasks.md` now explicitly require blank-`resource` and invalid-`size` GET tests.
- `T02-plan.md` and `tasks.md` now explicitly require a blank-`resource.type` POST test.

## Resolved material deltas

### 1. `actor.type` defaulting layer differs

**Previous spec:** `design.md` said the new POST `actor.type` field is optional and defaults to `USER`, with defaulting "done in the service, not the DTO."

**Resolution:** `design.md` now says defaulting happens while mapping the request into the domain model, not as a DTO field initializer. This matches the T02 plan while preserving the domain invariant that service code receives `Actor.type` as non-null.

**Outstanding action:** None.

### 2. `size <= 0` behavior differs

**Previous spec:** `design.md` validation rules said `size` is an integer `>= 1`, defaults to `50`, and is silently capped at `500`.

**Resolution:** `design.md` now states that values above `500` are capped, values below `1` return `400`, and non-integer `size` returns `400` with `field = "size"`. `T07-plan.md` now rejects lower-bound violations instead of clamping them.

**Outstanding action:** None.

## Resolved coverage gaps

### 3. GET blank `resource` is not explicitly tested

**Spec:** `design.md` says blank `actor` and blank `resource` query parameters return `400 Bad Request`.

**Plan:** `T07-plan.md` implements both checks in `SearchQuery`, but the listed controller ITs only include `getRejectsBlankActor`. There is no matching `getRejectsBlankResource` test.

**Resolution:** `T07-plan.md` and `tasks.md` now require `getRejectsBlankResource`.

### 4. POST blank `resource.type` is not explicitly tested

**Spec:** `design.md` says `resource.type` is optional, but if present it must be non-blank.

**Plan:** `T02-plan.md` routes request data through the domain `Resource` compact constructor, which rejects blank `type`, but the listed integration tests cover blank `actor.id` and blank `resource.id` only. There is no explicit blank `resource.type` test.

**Resolution:** `T02-plan.md` and `tasks.md` now require `postRejectsResourceTypeBlankWhenResourcePresent`.

## Non-deltas noted

- The step plans intentionally leave intermediate states that do not yet satisfy the final `GET /audit-events` design, especially T02 keeping offset pagination until T07. This is sequencing, not a final spec mismatch.
- T04 notes that it can compile without T03 even though `tasks.md` lists T04 after T03. That is a task-dependency refinement, not a difference from `requirements.md` or `design.md`.
