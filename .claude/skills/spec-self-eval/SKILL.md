---
name: spec-self-eval
description: Self-evaluate a single spec under `.specs/<feature>/` against `.specs/SPEC_WORKFLOW.md`
  and `AGENTS.md`, and write a PASS/FAIL/PARTIAL report to `.specs/<feature>/eval-report-<date>.md`.
  Use when the user asks to "evaluate the spec", "audit the spec", "review the spec quality",
  "check spec against SPEC_WORKFLOW", or "score the spec". Do NOT use for code review,
  PR review, security review, or anything outside `.specs/`.
metadata:
  category: documentation-review
  scope: project
  tags: [specs, requirements, design, tasks, ear, review]
---

# Spec self-evaluation

Self-evaluate one feature spec under `.specs/<feature>/` and write a single file
`.specs/<feature>/eval-report-<date>.md` containing a PASS / FAIL / PARTIAL table.

## Scope guardrails

- **Touch only one file.** The only artifact this skill produces or modifies is
  `.specs/<feature>/eval-report-<date>.md` inside the target feature folder.
- **No production code, no migrations, no test changes.** This is a spec-only review.
- **One feature per invocation.** If the user asks to evaluate several specs, run the
  skill once per feature folder.
- **No new sections, no rewrites of `requirements.md` / `design.md` / `tasks.md`.** This
  skill diagnoses; it does not fix.

## Input

A single feature-folder path under `.specs/`, e.g. `.specs/query-api/`.
If the user doesn't supply one, ask which feature they mean — do not guess.

## Output contract

Write `.specs/<feature>/eval-report-<date>.md` with exactly this shape, where
`<date>` is the current local date in `YYYY-MM-DD` form:

```markdown
# Spec self-evaluation report

Scope: `.specs/<feature>/`

| Check | Result | Evidence |
|---|---|---|
| <Check name> | PASS \| FAIL \| PARTIAL | <one-line citation: file + what makes it pass/fail> |
| ...                                                                   |
```

Rules for the table:

- One row per check in the rubric below — every check appears, even when the referenced
  file is missing (in that case `Result = FAIL`, `Evidence = "file missing"`).
- `Result` is exactly one of `PASS`, `FAIL`, `PARTIAL`. Use `PARTIAL` only when the
  criterion is met in part of the doc but violated elsewhere — quote both sides briefly.
- `Evidence` is one short sentence with a backticked file reference (e.g.
  `` `tasks.md` ``) and the specific feature you observed. No multi-paragraph prose.
- Do not invent additional rows. Do not add commentary outside the table.

If a previous `eval-report-<date>.md` exists for the same feature and date,
**overwrite** it. The dated file is a snapshot of the current spec state for that
day; older dated reports are history and should not be rewritten.

## Rubric (fixed)

The reusable rubric summary is bundled at
`references/_eval-checklist.md`. Read it when you need a compact rule list
before writing the dated feature-local result table.

Apply these 13 checks in order. The first three reference `requirements.md`, the next
five reference `design.md`, the next three reference `tasks.md`, and the last two are
cross-cutting.

### Requirements

1. **Required sections present.** `requirements.md` contains a `Problem` paragraph,
   per-persona / per-role acceptance criteria, and an explicit `Out of scope` section.
   → `SPEC_WORKFLOW.md` § Document shape → `requirements.md`.
2. **ACs follow EAR style.** Every AC is one sentence using a single EAR pattern —
   *Ubiquitous* ("The system shall …"), *Event-driven* ("When …, the system shall …"),
   *State-driven* ("While …, the system shall …"), *Optional feature* ("Where …, the
   system shall …"), *Unwanted* ("If …, then the system shall …"), or *Complex*
   (combination). No BDD Given/When/Then, no free-form prose, no bulleted half-sentences.
   → `SPEC_WORKFLOW.md` § Acceptance criteria — EAR style.
3. **ACs are atomic and observably testable.** Each AC describes one externally
   observable behavior that a test could falsify. ACs that bundle multiple behaviors,
   or that describe internal implementation, fail this check.

### Design

4. **Query and pagination strategy is justified when relevant.** For search/list
   endpoints, `design.md` explains the pagination strategy, deterministic ordering,
   tiebreakers, next-page detection, query count behavior, and indexes required to
   support the documented filters. If the spec has no search/list behavior, record
   `PASS` with evidence that the check is not applicable.
5. **API contract is specified.** `design.md` enumerates endpoints, request/response
   shapes, and status codes (when the problem is API-shaped).
6. **Data model and validation are specified.** `design.md` covers schema changes
   (Flyway migrations, indexes, tiebreakers) and per-field / per-endpoint validation
   rules. → `AGENTS.md` § Persistence rules.
7. **Layer integration is mapped.** `design.md` describes touchpoints in
   `controller / service / domain / persistence`, respecting the boundaries in
   `AGENTS.md` § Architecture (no `org.springframework.*` or
   `jakarta.persistence.*` in `domain/`; DTOs and JPA entities distinct from domain).
8. **AGENTS.md alignment is explicit.** `design.md` contains a table or section
   mapping each relevant invariant from `AGENTS.md` (append-only, server-set timestamp,
   required `actor`, build-health invariants, etc.) to how the design honors it.

### Tasks

9. **Every task has `Refs` to ACs and design sections.** Each numbered step in
   `tasks.md` cites the specific AC(s) from `requirements.md` and the specific design
   section(s) from `design.md` it implements. No orphan tasks.
10. **Every task has a testable `Definition of Done`.** DoD is an explicit bullet list
   naming tests that must pass, migrations that must run clean, or behaviors that
   must be observable. "Code compiles" or "looks done" fails this check.
11. **Every task lists `Dependencies` (or is marked independent).** Each step states
    which earlier step IDs it depends on, or explicitly says it has no prerequisites
    so it can be parallelized.

### Cross-cutting

12. **AC coverage is complete.** Every AC in `requirements.md` is exercised by at
    least one task in `tasks.md`. A "coverage check" table or equivalent mapping in
    `tasks.md` is the strongest evidence; absence of any AC from the task plan fails
    this check.
13. **Open questions are resolved once all specs are ready.** When all three of
    `requirements.md`, `design.md`, and `tasks.md` exist, every item under
    `requirements.md` § Open questions either has a recorded resolution (typically a
    pointer into `design.md` — e.g. "Resolved in `design.md` § …") or has been
    removed. Per `SPEC_WORKFLOW.md` § Document shape → `requirements.md`, Open
    questions are "anything that survived clarification and still needs a decision
    *before `design.md` starts*"; once `design.md` and `tasks.md` are present, no
    item may still be in the "needs a decision" state. If any of the three docs is
    missing, record this row as `PASS` with evidence noting which doc is missing —
    the rule does not apply until the spec set is complete.

## Procedure

1. **Resolve the target.** Confirm the feature folder path. If
   `.specs/<feature>/` does not exist, stop and report it — do not create a folder.
2. **Read the three core docs** if present: `requirements.md`, `design.md`, `tasks.md`.
   Missing docs are not an error condition for the skill itself; they produce `FAIL`
   rows for any check that depends on them.
3. **Run the rubric** in order. For each check, find concrete evidence (a section
   heading, a line, a missing element) and decide `PASS` / `FAIL` / `PARTIAL`.
4. **Write `eval-report-<date>.md`** inside the feature folder. Overwrite only the
   same-date report. Do not modify any other file under `.specs/` or anywhere else.
5. **Report briefly** to the user: how many PASS / FAIL / PARTIAL, and the path to
   the written file. Do not paste the full table back into chat unless asked.

## Examples

### Trigger phrases (should activate)

- "Evaluate the spec under `.specs/query-api/`."
- "Audit the query-api spec against SPEC_WORKFLOW."
- "Score the retention-archive spec."
- "Check whether the spec is ready to start coding."

### Should NOT trigger

- "Review this PR" → use `/review`.
- "Security review of the branch" → use `/security-review`.
- "Refactor this Java class" → use `java-refactoring`.
- "Write the spec for X" → this skill diagnoses existing specs; it does not author them.

## Anti-patterns

- Do not edit `requirements.md`, `design.md`, or `tasks.md` to "fix" findings. The
  skill reports; the human (or a separate task) fixes.
- Do not pad the table with checks not in the rubric. Stable rubric = comparable
  snapshots across features and across time.
- Do not write `PARTIAL` to avoid making a call. Use `PARTIAL` only when there is
  evidence on both sides; otherwise pick `PASS` or `FAIL`.
- Do not write or update `eval-checklist.md`; current self-evaluation output belongs
  in `.specs/<feature>/eval-report-<date>.md`.
- Do not write an `eval-report-<date>.md` outside the feature folder
  (e.g. at `.specs/eval-report-<date>.md`). One report per feature, inside the feature.
