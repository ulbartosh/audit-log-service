# Spec self-evaluation checklist

Scope: every `.specs/<feature>/` folder.

Use these as general rules when producing a dated feature-local
`eval-report-<date>.md`.
Each generated report should still record concrete `PASS`, `FAIL`, or `PARTIAL`
results with one-line evidence from the feature's own spec files.

| Check | General rule |
|---|---|
| Required sections present | `requirements.md` has a Problem paragraph, persona-scoped acceptance criteria, and an explicit Out of scope section. |
| ACs follow EAR style | Every AC is a single EAR sentence, not BDD Given/When/Then, prose, or a half-sentence bullet. |
| ACs are atomic and observably testable | Each AC describes one externally visible behavior that a test could falsify. |
| Query and pagination strategy is justified when relevant | Search/list specs explain pagination shape, deterministic ordering, tiebreakers, next-page detection, count-query behavior, and indexes for documented filters. |
| API contract is specified | API-shaped specs enumerate endpoints, request/response shapes, optional field behavior, and status codes. |
| Data model and validation are specified | Schema changes, index choices, tiebreakers, and per-field/per-endpoint validation rules are explicit. |
| Layer integration is mapped | `design.md` maps controller, service, domain, and persistence touchpoints without violating architecture boundaries. |
| AGENTS.md alignment is explicit | `design.md` maps relevant repo invariants such as append-only storage, server-set timestamps, required actor, Flyway-only schema changes, and build health. |
| Every task has `Refs` to ACs and design sections | Each numbered task cites the ACs and design sections it implements; preparatory tasks name the later ACs they enable. |
| Every task has a testable `Definition of Done` | DoD lists concrete tests, migrations, checks, or observable behaviors, not just "compiles" or "looks done". |
| Every task lists `Dependencies` | Each task lists prerequisite task IDs or explicitly says it is independent. |
| AC coverage is complete | Every AC is mapped to at least one implementation task and at least one test obligation. |
| Open questions are resolved once all specs are ready | Once requirements, design, and tasks exist, unresolved questions are removed or explicitly resolved with a pointer to the design decision. |
