# Audit Log Service — Agent Guide

## About the project

An internal service that receives audit events from other company services and stores them immutably. It exists for compliance, security, and observability, and is read by compliance officers, SREs, and security analysts.

## Tech stack

- Java 21
- Spring Boot 3
- Gradle (Kotlin DSL)
- PostgreSQL via Flyway migrations
- JUnit 5 for unit tests
- Testcontainers for integration tests

## Project map

- Base package: `com.training.bartosh` — every production class starts with this prefix.
- Production sources: `src/main/java/`
- Resources (incl. Flyway migrations under `db/migration/`): `src/main/resources/`
- Tests: `src/test/java/` (unit tests) and `src/test/java/` with `*IT.java` suffix or a dedicated `integrationTest` source set for Testcontainers-backed tests.

## Architecture

Layered architecture with one-way dependencies (`controller → service → domain ← persistence`). The domain is the core; outer layers depend on it, never the reverse.

```
com.training.bartosh.auditlog
├── controller/     REST controllers, request/response DTOs, exception handlers
├── service/        Use-case services that orchestrate domain + persistence
├── domain/         AuditEvent + value objects, domain invariants — no Spring, no JPA
├── persistence/    JPA entities, Spring Data repositories, domain↔entity mappers
└── config/         @Configuration beans, @ConfigurationProperties classes
```

Boundaries to keep:

- `domain/` is pure Java — no `org.springframework.*`, no `jakarta.persistence.*` imports.
- `controller/` never reaches into `persistence/` directly; it goes through `service/`.
- DTOs (in `controller/`) and JPA entities (in `persistence/`) are distinct types from the domain model. Map at the boundary.

Persistence rules:

- Schema changes only via new Flyway migrations named `V{n}__{snake_case_description}.sql`. Never edit a migration that has already been merged — add a new one.
- The audit event table is append-only; no `UPDATE` / `DELETE` statements in migrations or repositories against it.

## Functional requirements

- Ingest a single event: `POST /audit-events`
- Search by `actor` / `resource` / time range
- Retention policy: keep for N days, then archive
- (Stretch) Tamper-evidence via hash chain

## Event schema

- `timestamp` — when it happened. Set by the server; client-supplied values are ignored.
- `actor` — who initiated it (user ID / service account). Required.
- `action` — what they did (`resource.updated`, `user.login`, …).
- `resource` — what was acted on (`project:42`, `invoice:777`).
- `outcome` — `success` / `denied` / `error`.
- `context` — arbitrary JSON with details.

## Functional invariants

- Append-only: no `UPDATE`, no `DELETE` on stored events.
- `timestamp` is set only by the server.
- `actor` is required; reject events without one.

## Build health invariants (must hold before every commit)

A commit must leave the build green. Before committing, verify:

1. `./gradlew build` passes — compiles, runs tests, runs checks.
2. `./gradlew test` — all unit tests green.
3. `./gradlew check` — static analysis and style checks pass; no new compiler warnings.
4. Integration tests covering changed endpoints/queries pass (Testcontainers + Postgres, not mocks).
5. New code is tested. Bug fixes ship with a regression test. New endpoints ship with at least one integration test.
6. Flyway migrations apply cleanly from an empty database; no edits to already-merged migrations.
7. No commented-out code, no `System.out.println`, no `TODO` without an issue reference.
8. No secrets, `.env` files, or local IDE state added to the commit.
9. Test coverage (JaCoCo, line counter on merged `test` + `integrationTest` exec data, excluding the Spring Boot entry point and `config/` wiring) is **≥ 90%**. `./gradlew check` runs `jacocoTestCoverageVerification` and fails the build below this threshold.

If any step fails, fix the root cause. Do not bypass with `--no-verify`, `@Disabled`, or by deleting failing assertions.

## PR invariants (must hold for every pull request)

A PR is the unit of merge into `main`. These rules apply on top of the build-health invariants above and govern the review process itself.

1. PR review comments are addressed in the same change that fixes them, and the corresponding GitHub review thread is marked **resolved** once the fix is pushed. A push that responds to a comment but leaves the thread open is incomplete.
2. **One task, one branch, one PR.** Every numbered task — whether it lives in repo-root `PLAN.md` or in a spec's `tasks.md` (see `.specs/SPEC_WORKFLOW.md`) — lands on its own feature branch and is merged through its own PR. Tasks must not be combined on a single branch — small, focused PRs make review, revert, and bisection tractable. **Branch naming:** `<feature-name>/t<task-number>-<short-name-of-task>`, where `<feature-name>` is the spec folder name (for spec-tracked work) or a short slug for the feature (for `PLAN.md`-tracked work), and `<task-number>` is the zero-padded task ID from `tasks.md` / `PLAN.md`. Examples: `query-api/t02-structured-shape`, `retention-archive/t01-v4-migration`. Mid-PR review fixes for the same task stay on the same branch.
3. Before creating a new feature branch, **fetch and fast-forward `main` first** (`git fetch origin && git checkout main && git pull --ff-only`), then branch from the up-to-date tip. Branching from a stale `main` produces accidental rebase noise and conflicts that the squash-merge of the previous PR has already resolved.
4. Keep `README.md` fresh in the same PR whenever user-facing or operator-facing behavior changes. At minimum, update README when the API contract, quickstart/run flow, ports/URLs, required env vars, local test/build commands, hooks, CI-visible verification flow, or key documentation entry points change. If a code change genuinely needs no README update, say so explicitly in the PR/PLAN context.
5. **Spec acceptance criteria are satisfied before merge.** When a PR implements work tracked in `.specs/<problem-name>/`, every acceptance criterion in that spec's `requirements.md` must be (a) implemented and (b) covered by at least one test that would fail if the AC were violated. The PR description should map each AC to the test(s) that exercise it. If an AC is intentionally deferred, mark it explicitly in the PR and in the spec, with a follow-up reference; do not silently skip.

## Clarifying questions (before any non-trivial work)

Before drafting anything substantial — code, specs, plans, designs, docs, migrations, configuration, non-trivial refactors — ask 5–7 short clarifying questions about the decisions you would otherwise make by default. One decision = one question. If you have no real doubt about a particular choice, do not invent a question for it. Do not start writing until the user answers.

Scope:

- **Applies to:** new features, new files, new specs/designs/plans, schema or migration changes, architectural choices, behavior changes, anything where a reasonable person might choose differently than your default.
- **Does not apply to:** trivial edits the user has already specified (typo fixes, literal rename, an explicit one-line change), formatting, or carrying out an instruction the user has already pinned down.

When in doubt, ask. Cost of asking is low; cost of writing the wrong thing is high.

## Working with specifications

New problems are planned under `.specs/<problem-name>/` before any code is written. The workflow — folder layout and the `requirements.md` → `design.md` → `tasks.md` progression — lives in [`.specs/SPEC_WORKFLOW.md`](.specs/SPEC_WORKFLOW.md). Read it before drafting or editing any file under `.specs/`. The 5–7 clarifying-questions ritual (see section above) applies before every spec document.

## Working with PLAN.md

When an agent executes work tracked in `PLAN.md`, append a short result of execution at the end of every step. Keep it to 1–3 lines describing what was done, the outcome (pass/fail), and any follow-up needed. This keeps the plan self-documenting so the next agent can pick up cold.

## Negative invariants (guiding principles)

- Do no harm
- Do no lie
- Do not take what does not belong
- Do not accumulate more than necessary
- Conserve energy

## Positive invariants (guiding principles)

- Cleanliness of code and context
- Sufficiency of the solution
- Verification discipline
- Self-learning
- Serving something greater than the task
