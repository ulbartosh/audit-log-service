# Spec evaluation checklist

Scope: `.specs/query-api/`

| Check | Result | Evidence |
|---|---|---|
| Required sections present | PASS | `requirements.md` contains a Problem section, persona-specific acceptance criteria, and an Out of scope section. |
| ACs follow EAR style | PASS | `requirements.md` writes each AC as a single "When...", "If..., then...", or "The system shall..." sentence. |
| ACs are atomic and observably testable | PASS | `requirements.md` describes externally observable request, response, filtering, ordering, and pagination behavior. |
| Pagination strategy is justified | PASS | `design.md` chooses keyset pagination and explains the `(occurred_at DESC, id DESC)` tiebreaker, cursor predicate, concurrent-ingest stability, and `size + 1` next-page detection. |
| API contract is specified | PASS | `design.md` specifies `GET /audit-events`, changed `POST /audit-events` fields, request/response shapes, and status codes. |
| Data model and validation are specified | PASS | `design.md` defines the V4 migration, index shapes, column rules, and GET/POST validation rules. |
| Layer integration is mapped | PASS | `design.md` maps changes across `domain/`, `persistence/`, `service/`, and `controller/`. |
| AGENTS.md alignment is explicit | PASS | `design.md` includes an AGENTS.md alignment table covering domain purity, append-only persistence, Flyway, layering, and build health. |
| Every task has `Refs` to ACs and design sections | PASS | `tasks.md` gives each numbered task a Refs section with design links and relevant AC tags or preparatory AC targets. |
| Every task has a testable `Definition of Done` | PASS | `tasks.md` gives every numbered task a Definition of Done with concrete builds, tests, migrations, or observable behaviors. |
| Every task lists `Dependencies` | PASS | `tasks.md` marks task 01 independent and lists dependencies for tasks 02 through 07. |
| AC coverage is complete | PASS | `tasks.md` includes a Coverage check table mapping every requirements AC to an implementation and test step. |
| Open questions are resolved once all specs are ready | PASS | `requirements.md` records that all open-question resolutions are in `design.md` after the Open questions list. |
