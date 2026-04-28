# Audit Log Service — Implementation Plan

## Context

`agents.md` specifies an internal append-only audit-log service (Java 21 / Spring Boot 3 / Postgres / Flyway) with three required endpoints (ingest, search, retention) and a stretch goal (hash-chain tamper evidence). The repository is greenfield — only `agents.md` and an empty IntelliJ workspace exist. Goal: deliver a vertical slice that satisfies the functional requirements and the "build health invariants" gate (clean `./gradlew build`, integration tests via Testcontainers, append-only enforced in schema).

Build is staged so each phase is independently mergeable with green tests; phases B and C should be separate commits on top of A.

## Step result convention

Per `agents.md` ("Working with PLAN.md"), every executed step here gets a 1–3 line **Result:** note appended directly under it: what was done, outcome (pass/fail), and any follow-up. Keeps the plan self-documenting for the next agent.

## Execution log

### Step 0 — Update `agents.md` with PLAN.md execution convention (2026-04-27)

- Added a "Working with PLAN.md" section to `agents.md` instructing agents to append a short result of execution at the end of every plan step.
- **Result:** Done. `agents.md` updated with the new section above "Negative invariants". No code changes; no follow-up.
- **Result (2026-04-28, follow-up):** Added a PR invariant requiring `README.md` to stay current in the same change whenever API behavior, quickstart/run flow, ports/URLs, env vars, test/build commands, hooks, CI-visible verification flow, or documentation entry points change. This codifies the doc-consolidation work already reflected in the README/RUNNING merge. No follow-up.

## Decisions (defaults — flag at review if you want to change)

- **Records over classes** for the domain (Java 21 makes them ergonomic; immutability matches append-only).
- **Spring Data JPA + Specifications** for search (optional `actor` / `resource` / time-range filters compose cleanly). Listed in `agents.md` as the persistence approach.
- **Retention via separate `audit_events_archive` table**, populated by a daily scheduler. The "no DELETE" functional invariant applies to *user-facing* operations; retention is a system policy that moves cold rows out of the hot table. This will be called out in code comments where the DELETE happens.
- **Hash chain (stretch) deferred** to phase D — not in the first delivery.
- **Spotless + google-java-format** for the `./gradlew check` style gate. No Checkstyle (overlapping concerns).
- **No authn/authz** in this iteration — `agents.md` doesn't specify any, and audit-log services typically sit behind a network boundary.
- **Separate `integrationTest` source set** (`src/integrationTest/java/`) instead of mixing ITs into `src/test/java/`. Keeps `./gradlew test` fast (no Docker pull on every run), gives CI a natural stage split (unit → integration), and prevents Testcontainers utilities from leaking into unit tests. Updates A1 (Gradle config) and A8 (test paths). Supersedes the `agents.md` mention of `*IT.java` under `src/test/java/`.

## Phase A — Vertical slice (POST + GET, end-to-end)

Goal: a request hits `POST /audit-events`, persists to Postgres, and is retrievable via `GET /audit-events`.

### A1. Build & project scaffolding

- `settings.gradle.kts` — `rootProject.name = "audit-log-service"`
- `build.gradle.kts` — Kotlin DSL, `java { toolchain { languageVersion = 21 } }`, plugins: `org.springframework.boot 3.4.x`, `io.spring.dependency-management`, `com.diffplug.spotless`. Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core` + `flyway-database-postgresql`, test: `spring-boot-starter-test`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`.
- `gradle/wrapper/` — generate via `gradle wrapper --gradle-version 8.10` (this is one of the few external commands the user must approve at execute time; falls back to manually placing `gradle-wrapper.jar` if needed).
- `.gitignore` — standard Java/Gradle/IntelliJ ignores.
- Spotless config: `spotless { java { googleJavaFormat() } }` so `./gradlew check` enforces it (target updated to also cover `src/integrationTest/**/*.java`).
- **`integrationTest` source set** in `build.gradle.kts`:
  - Register `sourceSets { create("integrationTest") { ... } }` with compile/runtime classpaths extending `sourceSets.main.output` and `sourceSets.test.output` (so IT can reuse test fixtures).
  - Configurations: `integrationTestImplementation` extends `testImplementation`; same for `runtimeOnly`.
  - Task: `tasks.register<Test>("integrationTest") { useJUnitPlatform(); shouldRunAfter(tasks.test); testClassesDirs = sourceSets["integrationTest"].output.classesDirs; classpath = sourceSets["integrationTest"].runtimeClasspath }`.
  - Wire into the gate: `tasks.named("check") { dependsOn("integrationTest") }` so `./gradlew build`/`check` still runs ITs locally and in CI, but `./gradlew test` stays unit-only.
  - Spotless `target("src/**/*.java")` already covers the new folder.

**Result (2026-04-27):** Done. `settings.gradle.kts`, `build.gradle.kts` (Spring Boot 3.4.1, dependency-management 1.1.7, spotless 6.25.0, Java 21 toolchain, all listed deps incl. `spring-boot-testcontainers`), `gradlew`/`gradlew.bat` + `gradle/wrapper/gradle-wrapper.{jar,properties}`, and `.gitignore` are in place. Spotless `googleJavaFormat()` configured against `src/**/*.java`. (A `gradle.properties` originally pinned Gradle's toolchain at a Homebrew path; removed during the PR review cycle — the file was machine-specific and broke portability. See the **PR Review Resolution Log** at the end of this file.)

**Result (2026-04-27, follow-up):** Done. Added the `integrationTest` source set to `build.gradle.kts`: source set with main+test outputs on classpath, `integrationTestImplementation`/`integrationTestRuntimeOnly` extending the `test*` configurations, a `Test`-typed `integrationTest` task in the `verification` group with `shouldRunAfter(tasks.test)`, and `check` depending on it. Created `src/integrationTest/java/` and `src/integrationTest/resources/` directories. Verified via `./gradlew tasks --group verification` (task is registered) and `./gradlew help --task integrationTest`. No follow-up.

### A2. Application bootstrap

- `src/main/java/com/training/bartosh/auditlog/AuditLogApplication.java` — `@SpringBootApplication` with `main`.
- `src/main/resources/application.yml` — Postgres datasource (env-driven URL/user/pass), Flyway enabled, JPA `ddl-auto=validate` (Flyway owns schema), Jackson timestamps as ISO-8601, server port 8080.
- `src/main/resources/application-test.yml` — overrides for the integration profile (Testcontainers will inject the URL via `@DynamicPropertySource`).
- `config/AuditLogProperties` — `@ConfigurationProperties("auditlog")` with `retention.days` (default 365). Wired in phase C.

**Result (2026-04-27):** Done. `AuditLogApplication` created with `@SpringBootApplication` + `@ConfigurationPropertiesScan` (so `AuditLogProperties` is picked up without an explicit `@EnableConfigurationProperties`). `application.yml` wires env-driven datasource (`AUDITLOG_DATASOURCE_URL/USERNAME/PASSWORD` with localhost defaults), `ddl-auto=validate`, Flyway enabled, `open-in-view=false`, Hibernate UTC time zone, ISO-8601 Jackson dates, port 8080, and `auditlog.retention.days=365`. `application-test.yml` keeps Flyway + `validate` and notes Testcontainers will inject the URL in A8. `AuditLogProperties` is a record with a nested `Retention(int days)` record. No follow-up.

### A3. Schema — Flyway V1

`src/main/resources/db/migration/V1__create_audit_events.sql`:

```sql
CREATE TABLE audit_events (
    id          UUID        PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    resource    TEXT,
    outcome     TEXT        NOT NULL,
    context     JSONB
);
CREATE INDEX idx_audit_events_actor_time    ON audit_events (actor, occurred_at DESC);
CREATE INDEX idx_audit_events_resource_time ON audit_events (resource, occurred_at DESC);
CREATE INDEX idx_audit_events_time          ON audit_events (occurred_at DESC);

-- Append-only enforcement at the DB layer.
CREATE RULE audit_events_no_update AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
-- DELETE is permitted but only used by the retention scheduler (phase C).
-- The functional "no DELETE" invariant is enforced in application code by
-- never exposing a delete operation through controller/service.
```

`outcome` is stored as TEXT (mapped to enum in Java) to avoid a Postgres ENUM migration headache.

**Result (2026-04-27):** Done. `V1__create_audit_events.sql` created with the table, three indexes, and the `audit_events_no_update` rule. Verified by `FlywayMigrationIT` (asserts table + all three indexes exist after Flyway runs against a fresh container). No follow-up.

### A4. Domain layer (`domain/`)

- `domain/AuditEvent` — `record AuditEvent(UUID id, Instant occurredAt, String actor, String action, String resource, Outcome outcome, JsonNode context) {}` with a compact constructor that asserts `actor != null && !actor.isBlank()` and `action != null && !action.isBlank()`.
- `domain/Outcome` — `enum Outcome { SUCCESS, DENIED, ERROR }`.
- `domain/NewAuditEvent` — record carrying client-supplied fields only (no `id`, no `occurredAt`); the service mints those.

No Spring or JPA imports in this package (boundary rule from `agents.md`).

**Result (2026-04-27):** Done, TDD. Wrote `AuditEventTest` first (11 cases: null/blank rejection on actor, action, id, occurredAt, outcome; happy path; `NewAuditEvent` defaults outcome to SUCCESS when null; `NewAuditEvent` rejects blank actor/action). Implemented `Outcome`, `AuditEvent` (compact constructor enforces invariants), and `NewAuditEvent` (defaults outcome to SUCCESS). All 11 tests green. Domain has no `org.springframework` / `jakarta.persistence` imports (Jackson `JsonNode` only). No follow-up.

### A5. Persistence layer (`persistence/`)

- `persistence/AuditEventEntity` — `@Entity @Table(name="audit_events")`, fields mirror the table. JSONB via `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6 native).
- `persistence/AuditEventRepository extends JpaRepository<AuditEventEntity, UUID>, JpaSpecificationExecutor<AuditEventEntity>`.
- `persistence/AuditEventSpecifications` — static factory methods `byActor`, `byResource`, `between(from, to)`. Composed in service layer with `Specification.where(...)`.
- `persistence/AuditEventMapper` — pure functions `toDomain(entity)` / `toEntity(domain)`. No Spring beans needed; can be a final class with static methods.

**Result (2026-04-27):** Done. `AuditEventEntity` (UUID id, `@Enumerated(EnumType.STRING)` outcome, JSONB context via `@JdbcTypeCode(SqlTypes.JSON)`, protected no-arg constructor for JPA), `AuditEventRepository extends JpaRepository + JpaSpecificationExecutor`, `AuditEventSpecifications` (static `byActor`, `byResource`, `occurredAtOrAfter`, `occurredAtOrBefore`), and `AuditEventMapper` (final class, static `toDomain` / `toEntity`). No unit tests at this layer; coverage comes from `AuditEventControllerIT` and `FlywayMigrationIT`. No follow-up.

### A6. Service layer (`service/`)

- `service/AuditEventService`:
  - `AuditEvent record(NewAuditEvent input)` — generate UUID, set `occurredAt = Instant.now()` (from injected `Clock` for test seam), validate via domain constructor, save via repo, return mapped domain object.
  - `Page<AuditEvent> search(SearchQuery query, Pageable pageable)` — compose Specifications from non-null filters, default sort by `occurredAt DESC`.
- `service/SearchQuery` — record `(String actor, String resource, Instant from, Instant to)`; all nullable.
- `Clock` bean exposed in `config/` (`Clock.systemUTC()`), so tests can substitute a fixed clock.

**Result (2026-04-27):** Done, TDD. Wrote `AuditEventServiceTest` first (3 Mockito-based cases: clock-set timestamp on returned event, generated UUID id, persisted entity captured with the clock timestamp). Implemented `AuditEventService` (`@Transactional` write, `@Transactional(readOnly = true)` search using `Specification.allOf(...)` over the non-null filters), `SearchQuery` record, and `ClockConfig` (`Clock.systemUTC()` bean). All 3 tests green. No follow-up.

### A7. Controller layer (`controller/`)

- `controller/AuditEventController`:
  - `POST /audit-events` → 201 Created with `Location: /audit-events/{id}` and body `AuditEventResponse`.
  - `GET /audit-events?actor=&resource=&from=&to=&page=&size=` → `PagedResponse<AuditEventResponse>`. Defaults: `page=0`, `size=50`, max 500.
- DTOs:
  - `controller/dto/CreateAuditEventRequest` — `@NotBlank actor`, `@NotBlank action`, `String resource`, `Outcome outcome` (defaults to SUCCESS if null), `JsonNode context`. Note: `timestamp` is **not** accepted from clients (functional invariant — server-set).
  - `controller/dto/AuditEventResponse` — full event including `occurredAt`.
  - `controller/dto/PagedResponse<T>` — `items`, `page`, `size`, `total`.
- `controller/GlobalExceptionHandler` — `@RestControllerAdvice`:
  - `MethodArgumentNotValidException` → 400 with field errors
  - `IllegalArgumentException` (domain invariants) → 400
  - `Throwable` → 500 with opaque body, full stack to log

**Result (2026-04-27):** Done. `AuditEventController` exposes `POST /audit-events` (defaults `outcome` to SUCCESS when omitted, returns 201 + `Location: /audit-events/{id}`) and `GET /audit-events?actor=&resource=&from=&to=&page=&size=` (default size 50, capped at 500, sorted by `occurredAt` DESC). DTOs: `CreateAuditEventRequest` (`@NotBlank actor`/`action`; no `timestamp` field — Jackson silently ignores client-supplied `timestamp`/`occurredAt` since Boot's default `fail-on-unknown-properties=false`), `AuditEventResponse`, `PagedResponse<T>`. `GlobalExceptionHandler` maps `MethodArgumentNotValidException` → 400 with `{errors:[{field,message}]}`, `IllegalArgumentException` → 400, fallback `Exception` → 500 (narrowed from `Throwable` to avoid swallowing `Error`s — flag at review if you want broader). Verified by `AuditEventControllerIT`. No follow-up.

### A8. Tests

- **Unit** (`src/test/java/`):
  - `domain/AuditEventTest` — invariant rejection (blank actor, blank action).
  - `service/AuditEventServiceTest` — fixed-`Clock` test, server-set timestamp, client-supplied timestamp ignored.
- **Integration** (`src/integrationTest/java/`, `*IT.java`, Testcontainers, run by `./gradlew integrationTest` or `./gradlew check`):
  - `AuditEventControllerIT` — Spring Boot test with `@Testcontainers` Postgres 16. Cases: POST happy path, POST rejected (missing actor → 400), POST timestamp-from-client ignored, GET filter by actor, GET filter by time range, pagination.
  - `FlywayMigrationIT` — boots context against fresh container, asserts `audit_events` table + indexes exist.
- Shared base: `AuditLogIntegrationTest` abstract class (in the `integrationTest` source set) with `@DynamicPropertySource` wiring the Postgres container URL.

**Result (2026-04-27):** Done. Used Spring Boot 3 `@ServiceConnection` instead of `@DynamicPropertySource` — cleaner. `TestcontainersConfiguration` (`@TestConfiguration(proxyBeanMethods=false)`) defines a `PostgreSQLContainer<>("postgres:16-alpine")` `@Bean` `@ServiceConnection`; Boot autostarts it and wires the datasource. Base class `AuditLogIntegrationTest` is `@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` (each test rolls back, isolating state — append-only DB rule still holds since rollback aborts the txn rather than issuing UPDATE/DELETE). `AuditEventControllerIT` covers all 8 listed cases (POST happy path + missing actor + client timestamp ignored + outcome default; GET filter by actor / resource / time range; pagination size cap + total). `FlywayMigrationIT` checks table existence via `DatabaseMetaData` and queries `pg_indexes` for the three named indexes. **All 24 tests green** (11 domain unit + 3 service unit + 8 controller IT + 2 migration IT). `./gradlew build` passes (compile + test + integrationTest + spotlessCheck). No follow-up.

### A9. OpenAPI / Swagger UI

Goal: an executable, browsable API contract — `/v3/api-docs` returns the OpenAPI 3 JSON, `/swagger-ui.html` (or `/swagger-ui/index.html`) serves the interactive UI. No authn in front of either; both are dev-only conveniences in this iteration.

- Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.x` to `build.gradle.kts` (Spring Boot 3 + Java 21 compatible; auto-configures the spec generator and bundles the Swagger UI webjar).
- `config/OpenApiConfig` — `@Bean OpenAPI auditLogOpenAPI()` setting `title`, `version`, and `description`. Keeps metadata in code rather than `application.yml` so it's typed and refactor-safe.
- `application.yml` — add `springdoc.api-docs.path: /v3/api-docs`, `springdoc.swagger-ui.path: /swagger-ui.html`, `springdoc.swagger-ui.operations-sorter: method` (defaults are fine; pin paths so the contract is explicit).
- IT (`OpenApiIT`) — asserts:
  - `GET /v3/api-docs` → 200, JSON body has `info.title == "Audit Log Service"`, and `paths./audit-events` has both `post` and `get`.
  - `GET /swagger-ui/index.html` → 200 and HTML contains `"Swagger UI"`.
- Manual verification (the "make swagger executable" gate): `./gradlew bootRun` then open `http://localhost:8080/swagger-ui.html` in a browser; the two `audit-events` operations should be invokable from the page against the running service.

**Result (2026-04-27):** Done. Added `springdoc-openapi-starter-webmvc-ui:2.7.0` to `build.gradle.kts`; created `config/OpenApiConfig` with the `OpenAPI` info bean; added `springdoc.*` block to `application.yml` pinning `/v3/api-docs` and `/swagger-ui.html` plus `operations-sorter: method`. New `OpenApiIT` (2 cases: `/v3/api-docs` exposes `/audit-events` post+get with the configured title; `/swagger-ui/index.html` returns 200 with "Swagger UI" in the body). `./gradlew build` green: 26 tests across 5 suites. Verified Swagger UI is reachable when `bootRun` is up at `http://localhost:8080/swagger-ui.html`. No follow-up.

### A10. Run / deploy convenience: Dockerfile, docker-compose, RUNNING.md

Goal: anyone with Docker can bring up the full stack (app + Postgres) without installing Java/Gradle. Operator-facing instructions live in a separate file so the architecture-focused `agents.md` and the implementation-focused `PLAN.md` don't drift.

- `Dockerfile` — multi-stage. Stage 1 (`eclipse-temurin:21-jdk`) runs `./gradlew bootJar -x test -x integrationTest --no-daemon` to produce the fat jar. Stage 2 (`eclipse-temurin:21-jre`) copies the jar and `ENTRYPOINT`s `java -jar /app/app.jar`. Test/IT skipped during image build because the build-health gate is enforced separately by CI / `./gradlew build`.
- `.dockerignore` — excludes `.gradle/`, `build/`, `.idea/`, `.vscode/`, `.claude/`, `out/`, `*.iml`, `.git/`, `.DS_Store` so the build context stays small and reproducible.
- `docker-compose.yml` — two services:
  - `db`: `postgres:16-alpine` with named volume `audit-pg-data`, env-driven `auditlog`/`auditlog`/`auditlog` credentials, healthcheck `pg_isready -U auditlog -d auditlog` (2 s interval, 30 retries). Host-side port is **`55432`** (mapped to container `5432`) to avoid colliding with a local Postgres install.
  - `app`: `build: .`, depends on `db` healthcheck, env-injects `AUDITLOG_DATASOURCE_*` so Boot connects via the compose network (`db:5432`), publishes `8080:8080`.
- `RUNNING.md` — operator guide covering: prereqs, `docker compose` mode, `./gradlew bootRun` mode, curl smoke checklist, Swagger UI walkthrough (step-by-step `Try it out` against the running service), test-suite commands, troubleshooting. *(Superseded 2026-04-28: contents folded into `README.md` and the standalone file deleted.)*

**Result (2026-04-27):** Done. Verified end-to-end: `docker compose build app` produced the image (multi-stage build, ~90 s); `docker compose up -d` brought up both services healthy; live smoke confirmed `POST /audit-events` → 201 with `Location` header, `GET /audit-events?actor=u1` returned the event, missing-actor → 400 with `{errors:[{field:"actor",message:"must not be blank"}]}`, `/v3/api-docs` returned the OpenAPI JSON with the configured title, `/swagger-ui.html` 302→`/swagger-ui/index.html`. Stack torn down with `docker compose down`. **Decision flagged:** host-side Postgres port is `55432` (the standard 5432 was already bound on this machine — README.md documents this and how to revert). No follow-up.

**Result (2026-04-28, doc consolidation):** Folded the standalone `RUNNING.md` into `README.md` and deleted `RUNNING.md`. README now carries the project description, tech stack, quickstart, full **API reference** (POST/GET schemas, query params, error shape, invariants), curl smoke checklist, local-dev / `bootRun` mode, test-and-gate commands, and the one-time hooks-activation step. Trimmed the long Swagger UI tutorial and the troubleshooting section — the API tables make Swagger self-explanatory and the troubleshooting cases were Docker-generic. README also adds explicit pointers to `agents.md` and `PLAN.md` so a new contributor can find architecture and history in two clicks.

### A11. CI + local hooks: pre-commit, pre-push, GitHub Actions

Goal: the build-health invariants from `agents.md` are enforced at three points — pre-commit (fast), pre-push (full), and on every PR via GitHub Actions. Same gradle commands at every layer so local and CI failures look identical.

- `.githooks/pre-commit` — `./gradlew test spotlessCheck`. Unit tests + formatter only; no Docker required so the hook never blocks on environment state. Executable.
- `.githooks/pre-push` — `./gradlew build`. Full gate (compile + test + integrationTest + spotlessCheck). Needs Docker for Testcontainers; deliberate bypass via `git push --no-verify` if Docker isn't available locally. Executable.
- Hooks are tracked under `.githooks/` rather than the unversioned `.git/hooks/`. Activation is one-time per clone: `git config core.hooksPath .githooks`. Documented in `README.md`.
- `.github/workflows/build.yml` — runs on `pull_request` to `main` and `push` to `main`. Steps: checkout, `setup-java@v4` (Temurin 21), `gradle/actions/setup-gradle@v4` (caching), `./gradlew --no-daemon build`. On failure, uploads `build/reports/tests/` and `build/test-results/` as an artifact for diagnosis. Docker is preinstalled on `ubuntu-latest`, so Testcontainers ITs run unchanged.

**Result (2026-04-27):** Done locally. Files created and `.githooks/*` chmod +x'd. Activation step is documented in `RUNNING.md` (skipped here because the safety contract forbids me running `git config`). Workflow not yet exercised — first run will be on the PR opened in this same step. **Follow-up:** verify the GitHub Actions run is green on the feature branch's PR; if it fails, the most likely culprit is the `org.gradle.java.installations.paths` pin in `gradle.properties` (the Homebrew path doesn't exist on the Linux runner). Boot's Docker build already proved Gradle falls back to `JAVA_HOME` and emits only a warning, so this should be fine, but flagging in case.

**Result (2026-04-27, follow-up):** Done. Split `.github/workflows/build.yml` into explicit CI stages: `formatting` (`spotlessCheck`), `unit-tests` (`test`), `integration-tests` (`integrationTest`), and `package` (`assemble`). Kept the existing gate strength the same while making failures easier to localize and ensuring Docker-backed integration tests only run after fast checks pass. No follow-up beyond exercising the workflow on GitHub.

### A12. ArchUnit — boundary rules enforced at build time

Goal: turn the prose boundaries in `agents.md` ("`domain/` is pure Java", "controller never reaches into persistence", layered one-way deps) into compile-blocking tests instead of code-review hygiene. Runs in the `test` task so it gates alongside unit tests (the `unit-tests` CI stage), not behind Docker.

- `testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")` in `build.gradle.kts`.
- `src/test/java/com/training/bartosh/auditlog/architecture/ArchitectureTest.java` — single class with four `@ArchTest` rules:
  1. `domain/` does not depend on `org.springframework..` (Spring kept out of the domain).
  2. `domain/` does not depend on `jakarta.persistence..` or `org.hibernate..` (JPA kept out of the domain).
  3. `controller/` does not depend on `..persistence..` (must go through `service/`).
  4. Layered architecture: `Controller → Service`, `Service → Persistence`, all may depend on `Domain`; nothing may access `Controller`, `Service` only from `Controller`, `Persistence` only from `Service`. `consideringOnlyDependenciesInLayers()` so root/config classes (e.g., `AuditLogApplication`, `OpenApiConfig`) don't trip the rule.
- Each rule has a `.because("agents.md: ...")` so a future failure points to the source of truth.

**Result (2026-04-27):** Done. Added archunit-junit5 1.3.0 dep; created the `architecture/ArchitectureTest`. All 4 rules pass on the current code (validates that the implementation already respects the documented boundaries). Total unit tests now 18 (11 domain + 4 architecture + 3 service); full `./gradlew build` green at 30 tests across 6 suites. No follow-up.

### A13. Append-only invariant covered by integration test

Goal: the agents.md "Append-only: no UPDATE on stored events" rule is enforced at the DB layer by `audit_events_no_update` (`DO INSTEAD NOTHING` in V1). Without a regression test, dropping or breaking that rule wouldn't fail the build.

- `src/integrationTest/.../persistence/AuditEventImmutabilityIT` — using the Testcontainers Postgres datasource:
  1. INSERT a row directly via JDBC.
  2. Attempt `UPDATE audit_events SET actor = 'tampered' WHERE id = ?`.
  3. Assert `executeUpdate()` returned 0 and the row's `actor` is unchanged.

**Result (2026-04-27):** Done. New IT passes; full build green at 31 tests across 7 suites. Locks in the DB-level UPDATE-rejection rule against accidental migration regressions. No follow-up.

### A14. JaCoCo coverage gate (≥ 90% line, merged unit + integration)

Goal: turn the new agents.md invariant ("Test coverage ≥ 90%") into a build-failing check, with a single merged report that combines the unit-test and Testcontainers-IT runs.

- `jacoco` plugin in `build.gradle.kts`, JaCoCo 0.8.12 (Java 21 compatible).
- Both `test` and `integrationTest` are `Test` tasks, so the plugin auto-instruments both. Their exec files (`build/jacoco/test.exec`, `build/jacoco/integrationTest.exec`) feed two custom-configured tasks:
  - `jacocoTestReport` — XML + HTML at `build/reports/jacoco/test/`.
  - `jacocoTestCoverageVerification` — fails the build if `LINE` `COVEREDRATIO` < 0.90.
- Class-dir filter excludes `AuditLogApplication` (Spring Boot entry point) and `config/**` (pure bean wiring) — both are exercised at startup but carry no testable branches.
- Both tasks wired into `check`, so `./gradlew build` enforces the gate.
- `agents.md` Build-health invariant #9 added: ≥ 90% line coverage on the merged exec data, build fails below.

**Result (2026-04-27):** Done. Full build green: **97.0% line coverage** (130/134 covered), well above the 90% gate. Per-package: domain 100%, service 100%, persistence 100%, controller/dto 100%, controller 88.9% (4 lines in `GlobalExceptionHandler.handleIllegalArgument` + `handleUnknown` not yet exercised). Above threshold so no tests added; flagged for a future Phase B polish if we want 100%. No follow-up.

**Result (2026-04-27, CI follow-up):** Coverage promoted into CI:
- `jacoco-exec-test` artifact uploaded by `unit-tests` job and `jacoco-exec-integrationTest` uploaded by `integration-tests` job (both `if: always()` so partial reports are still recoverable on failure).
- New `coverage` job (between `integration-tests` and `package`) downloads both exec files, runs `jacocoTestReport` + `jacocoTestCoverageVerification` with `-x test -x integrationTest`, and posts a per-package markdown table to `$GITHUB_STEP_SUMMARY` (Lines / Branches / Methods columns + a TOTAL row). The full HTML+XML report is also uploaded as a `coverage-report` artifact.
- `package` job's `needs:` updated to depend on `coverage` so we don't ship a jar that regressed coverage.

**Result (2026-04-27, agents.md PR invariant follow-up):** Captured a new PR-specific rule (now living under `agents.md` "PR invariants"): review comments must be addressed in the same change that fixes them and the corresponding thread marked resolved once pushed. Triggered by PR #1 receiving automated review comments (gemini-code-assist) that required both code fixes and explicit thread-resolution to keep the PR auditable. The actual resolutions are tracked in the **PR Review Resolution Log** at the end of this file, not as a numbered Phase A step.

## Phase B — Robustness polish

Small commit on top of A:

- Pagination ceiling enforced in controller (`size <= 500`).
- Validation message bodies follow a consistent shape (`{ "errors": [{ "field": "...", "message": "..." }] }`).
- Add `@JsonInclude(NON_NULL)` to responses.
- Tests for each new error path.

## Phase C — Retention

- New migration `V2__create_audit_events_archive.sql` — `audit_events_archive` (same columns + `archived_at TIMESTAMPTZ NOT NULL DEFAULT now()`).
- `service/RetentionService.archiveOlderThan(Duration)` — single transaction: `INSERT INTO archive (...) SELECT ... FROM main WHERE occurred_at < :cutoff RETURNING id`, then `DELETE FROM main WHERE id = ANY(:ids)`. Batched in chunks of 1000.
- `service/RetentionScheduler` — schedule pulled from configuration, not hardcoded:
  - `@Scheduled(cron = "${auditlog.retention.cron:0 0 3 * * *}", zone = "${auditlog.retention.zone:UTC}")`
  - calls service with `Duration.ofDays(properties.retention().days())`.
  - Property placeholders use defaults so the scheduler still works out-of-the-box (3 AM UTC daily) without `application.yml` overrides; operators can change the cadence without recompiling.
- Extend `config/AuditLogProperties.Retention` from `(int days)` to `(int days, String cron, String zone)` so the same record validates the values that the placeholders consume. Defaults set in `application.yml` (see below). Spring Boot validates the cron string at startup via `CronExpression.parse` when the bean is bound.
- `application.yml` adds:
  ```yaml
  auditlog:
    retention:
      days: 365          # already present
      cron: "0 0 3 * * *"
      zone: "UTC"
  ```
  Document that `cron` follows Spring's 6-field syntax (`second minute hour day-of-month month day-of-week`).
- Enable scheduling on the application class with `@EnableScheduling`.
- Integration test: insert events with manipulated `occurred_at`, invoke `RetentionService` directly (don't wait for cron), assert main has young rows only and archive has old rows. A second IT covers the property override path: set `auditlog.retention.cron=...` via `@DynamicPropertySource`, boot the context, and assert the resolved schedule on the registered `ScheduledTask` (or accept an alternative expression and verify no startup failure).
- Code comment at the DELETE call referencing the "no DELETE" invariant and explaining the carve-out for retention.

## Phase D — Stretch: hash chain (defer; do not implement now)

Sketch only, for later:

- `V3__add_hash_chain.sql` adds `previous_hash BYTEA`, `event_hash BYTEA NOT NULL`.
- `service/HashChainService` computes `event_hash = SHA-256(previous_hash || canonical_payload)`. Single global chain; on write, fetch current tail under `SELECT ... FOR UPDATE` to serialize.
- `GET /audit-events/integrity` recomputes and reports breakage.
- Skip until phases A–C are green; treat as a separate ticket.

## Critical files (created in this plan)

```
build.gradle.kts
settings.gradle.kts
gradle/wrapper/gradle-wrapper.properties
.gitignore
src/main/resources/application.yml
src/main/resources/application-test.yml
src/main/resources/db/migration/V1__create_audit_events.sql
Dockerfile
.dockerignore
docker-compose.yml
RUNNING.md
src/main/java/com/training/bartosh/auditlog/AuditLogApplication.java
src/main/java/com/training/bartosh/auditlog/config/AuditLogProperties.java
src/main/java/com/training/bartosh/auditlog/config/ClockConfig.java
src/main/java/com/training/bartosh/auditlog/config/OpenApiConfig.java
src/main/java/com/training/bartosh/auditlog/domain/AuditEvent.java
src/main/java/com/training/bartosh/auditlog/domain/NewAuditEvent.java
src/main/java/com/training/bartosh/auditlog/domain/Outcome.java
src/main/java/com/training/bartosh/auditlog/persistence/AuditEventEntity.java
src/main/java/com/training/bartosh/auditlog/persistence/AuditEventRepository.java
src/main/java/com/training/bartosh/auditlog/persistence/AuditEventSpecifications.java
src/main/java/com/training/bartosh/auditlog/persistence/AuditEventMapper.java
src/main/java/com/training/bartosh/auditlog/service/AuditEventService.java
src/main/java/com/training/bartosh/auditlog/service/SearchQuery.java
src/main/java/com/training/bartosh/auditlog/controller/AuditEventController.java
src/main/java/com/training/bartosh/auditlog/controller/GlobalExceptionHandler.java
src/main/java/com/training/bartosh/auditlog/controller/dto/CreateAuditEventRequest.java
src/main/java/com/training/bartosh/auditlog/controller/dto/AuditEventResponse.java
src/main/java/com/training/bartosh/auditlog/controller/dto/PagedResponse.java
src/test/java/com/training/bartosh/auditlog/architecture/ArchitectureTest.java
src/test/java/com/training/bartosh/auditlog/domain/AuditEventTest.java
src/test/java/com/training/bartosh/auditlog/service/AuditEventServiceTest.java
src/integrationTest/java/com/training/bartosh/auditlog/AuditLogIntegrationTest.java
src/integrationTest/java/com/training/bartosh/auditlog/TestcontainersConfiguration.java
src/integrationTest/java/com/training/bartosh/auditlog/OpenApiIT.java
src/integrationTest/java/com/training/bartosh/auditlog/controller/AuditEventControllerIT.java
src/integrationTest/java/com/training/bartosh/auditlog/persistence/AuditEventImmutabilityIT.java
src/integrationTest/java/com/training/bartosh/auditlog/persistence/FlywayMigrationIT.java
```

Phase C adds `V2__create_audit_events_archive.sql`, `service/RetentionService.java`, `service/RetentionScheduler.java`, plus a retention IT (under `src/integrationTest/java/...`).

## Verification (the build-health gate from agents.md)

Run after each phase; all must be green before commit:

1. `./gradlew build` — compiles, runs all tests (unit + integration), runs `check`.
2. `./gradlew test` — JUnit unit tests pass (fast, no Docker).
3. `./gradlew integrationTest` — Testcontainers-backed ITs pass (requires Docker).
4. `./gradlew check` — Spotless formatting + `test` + `integrationTest` pass; no new compiler warnings.
5. `./gradlew bootRun` followed by manual smoke:
   - `curl -X POST localhost:8080/audit-events -H 'Content-Type: application/json' -d '{"actor":"u1","action":"login","outcome":"SUCCESS"}'` → 201
   - `curl 'localhost:8080/audit-events?actor=u1'` → returns the event
   - `curl -X POST localhost:8080/audit-events -H 'Content-Type: application/json' -d '{"action":"login","outcome":"SUCCESS"}'` → 400 (missing actor)
   - Open `http://localhost:8080/swagger-ui.html` in a browser → renders the interactive UI with both `audit-events` operations and lets you `Try it out` against the running service.
   - `curl localhost:8080/v3/api-docs` → returns the OpenAPI 3 JSON spec.
6. Migrations apply cleanly from empty DB — covered by `FlywayMigrationIT`.

CI staging note: split `./gradlew test` and `./gradlew integrationTest` into separate jobs/steps so unit failures fail fast before Docker is provisioned.

## Out of scope

- AuthN/AuthZ
- Multi-tenant partitioning
- Async ingestion / batching endpoint
- Hash chain (Phase D, deferred)
- Cold-storage export to S3 (retention archives in-DB only)

## PR Review Resolution Log

Reverse-chronological log of PR review comments that have been addressed and resolved on GitHub. Per `agents.md` PR invariant #1 (review comments addressed and threads resolved in the same change as the fix). This is **not** a numbered phase step — it tracks an ongoing review process across the lifetime of a PR.

### PR #2 — `docs/agents-pr-invariant-one-step-one-pr` (2026-04-28)

Reviewer: `gemini-code-assist`. One thread, addressed in commit `6c0f9c3` and resolved on GitHub.

| # | File | Comment summary | Fix | Thread |
| --- | --- | --- | --- | --- |
| 1 | `agents.md` | The branch naming template used `<phase>` while one example used a step ID (`b1`), and "Independent steps" could be misread as allowing some step combinations. | Updated the invariant to `feat/<step-id>-<short-slug>` and tightened the prohibition to `Steps must not be combined on a single branch`. No `README.md` update needed: this is an internal PR-governance wording fix, not a user-facing or operator-facing behavior change. | resolved |

### PR #1 — `feat/phase-a-vertical-slice` (2026-04-27)

Reviewer: `gemini-code-assist[bot]`. Three threads, all addressed in commit `287da54` and resolved on GitHub via the GraphQL `resolveReviewThread` mutation.

| # | File | Comment summary | Fix | Thread |
| --- | --- | --- | --- | --- |
| 1 | `gradle.properties` | Hardcoded Homebrew toolchain path is non-portable. | Deleted the file. Defaults (Gradle reads `JAVA_HOME` + auto-downloads if needed) work on every machine; the file existed only to paper over a local toolchain quirk. | resolved |
| 2 | `controller/AuditEventController.java` | `req.outcome() == null ? Outcome.SUCCESS : req.outcome()` duplicates the default already enforced by `NewAuditEvent`'s compact constructor. | Pass `req.outcome()` straight through; let the domain record default. Removed now-unused `Outcome` import. | resolved |
| 3 | `controller/AuditEventController.java` | Hardcoded `URI.create("/audit-events/" + id)` for the `Location` header is fragile under context paths and proxy headers. | Replaced with `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(event.id()).toUri()`. Standard Spring idiom. | resolved |

Verification after the fixes:
- `./gradlew build` green; 32 tests pass; line coverage 97.0% (unchanged).
- Controller IT's `header().exists("Location")` assertion still holds with the new builder.

#### Second round (2026-04-28)

Reviewer: `gemini-code-assist[bot]`. Five threads, all addressed in this round and resolved on GitHub.

| # | File | Comment summary | Fix |
| --- | --- | --- | --- |
| 1 | `Dockerfile` | `COPY *.jar app.jar` is fragile if multiple jars exist in `build/libs/` (e.g. Boot's plain jar). | Pinned `bootJar` `archiveFileName` to `app.jar` in `build.gradle.kts`; Dockerfile now copies the exact filename, no wildcard. Also removed the stale `gradle.properties` reference from the Dockerfile (file was deleted in the prior PR review round). |
| 2 | `RUNNING.md` | Prereqs section still claimed `gradle.properties` pins the toolchain to Homebrew — file was removed. | Rewrote the bullet: Gradle's toolchain detection / `JAVA_HOME` / auto-download covers every dev machine; per-user pin goes in `~/.gradle/gradle.properties`, not the repo. |
| 3 | `GlobalExceptionHandler.handleIllegalArgument` | `Map.of("message", ex.getMessage())` NPE if `getMessage()` returns null. | Null-coalesced to `"Invalid request"` before passing to `Map.of`. |
| 4 | `GlobalExceptionHandler` | `@ExceptionHandler(Exception.class)` swallowed Spring's framework exceptions (e.g. `HttpMessageNotReadableException` for malformed JSON) as 500 instead of 4xx. | Class now extends `ResponseEntityExceptionHandler`, inheriting correct 4xx defaults for framework exceptions. Overrode `handleMethodArgumentNotValid` to keep our `{errors:[{field,message}]}` body shape. The `Exception` catch-all stays but only fires for genuine application errors. |
| 5 | `AuditEventSpecifications` | Hardcoded property strings (`"actor"`, `"resource"`, `"occurredAt"`) are not refactor-safe. | Added `annotationProcessor("org.hibernate.orm:hibernate-jpamodelgen")` so Hibernate generates `AuditEventEntity_` at build time; specs now reference `AuditEventEntity_.actor` / `.resource` / `.occurredAt` (typed `SingularAttribute`). A future field rename in the entity now fails at compile time. JaCoCo class-dir filter extended to exclude `**/*_.class`. |

Verification after the fixes:
- `./gradlew build` green; 32 tests pass; line coverage 96.3% (above the 90% gate; minor drop reflects new branches in the exception handler — still healthy).
- `docker compose build app` produced the runtime image with the renamed `app.jar`.

#### Third round (2026-04-28)

Reviewer: `gemini-code-assist[bot]`. Two threads, addressed in this round and resolved on GitHub.

| # | File | Comment summary | Fix |
| --- | --- | --- | --- |
| 1 | `controller/AuditEventController.java` | `Sort.by(..., "occurredAt")` is hardcoded and bypasses the JPA Metamodel benefit; reviewer suggests moving default sort into the service layer (where the metamodel is accessible) so renames are caught at compile time. | Controller now builds `PageRequest.of(safePage, cappedSize)` without a `Sort`. The service applies the default (`occurredAt DESC`) only when the incoming `Pageable` is unsorted, using the generated `AuditEventEntity_.OCCURRED_AT` constant — a future field rename in the entity now fails compilation. Removed `Sort` import from the controller; the layered-architecture rule (controller cannot depend on persistence) remains intact. |
| 2 | `controller/GlobalExceptionHandler.java` | Inconsistent error shapes — validation returned `{errors:[{field,message}]}`, others returned `{message:...}`. | Unified all error responses to `{errors:[{field?,message}]}`. Added a private `errorResponse(status, message)` helper used by `handleIllegalArgument` and `handleUnknown`. Field-level validation errors keep their `{field,message}` objects; other failures emit a single `{message}` object inside the same `errors` array. The controller IT's `jsonPath("$.errors").exists()` still holds. |

Verification after the fixes:
- `./gradlew build` green; 32 tests pass; line coverage 95.0% (above the 90% gate).
- The controller's `Sort` import is gone — `ArchitectureTest.controllerDoesNotAccessPersistence` still passes (the metamodel constant is referenced from the service layer, not the controller).

#### Fourth round (2026-04-28)

Reviewer: `gemini-code-assist[bot]`. One thread, addressed and resolved.

| # | File | Comment summary | Fix |
| --- | --- | --- | --- |
| 1 | `Dockerfile` | Dependency resolution and source copy were in the same layer; editing any file under `src/` invalidated the dep cache and forced a re-download on every rebuild. Reviewer suggested splitting deps into their own layer. | Reordered the builder stage so dependency resolution sits in its own layer ahead of `COPY src`. The new sequence: copy `gradlew`/build files/`gradle/` → run `./gradlew --no-daemon dependencies --quiet` → copy `src/` → `./gradlew bootJar`. Editing `src/` now only invalidates the last two layers; the deps layer stays cached as long as `build.gradle.kts` / `settings.gradle.kts` / `gradle/` are unchanged. |

Verification after the fixes:
- `docker compose build app --no-cache` produced a working image; the deps task ran once and `bootJar` finished in ~15 s using the warmed Gradle cache.
