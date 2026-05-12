So # Specification Workflow

How to plan a new problem in this repo before writing any code.

## Where specs live

- Root: `.specs/`
- **One folder per problem**: `.specs/<problem-name>/`
  - `<problem-name>` is a short slug describing the problem (e.g., `query-api`, `retention-archive`).
  - **No version suffixes** in folder names (`-v2`, `-version-2`, …). Iterate in place and rely on git history. If you keep personal alternates locally, do not commit them — the canonical folder is `.specs/<problem-name>/`.
- **Three documents per problem**, authored strictly in this order:
  1. `requirements.md` — what the problem is, who needs it, acceptance criteria, what's out of scope.
  2. `design.md` — how to solve it: API contract, data model, indexes, validation, layer integration, AGENTS.md alignment.
  3. `tasks.md` — the step-by-step execution plan, broken into PR-sized units.
- **Per-task plans** live under `.specs/<problem-name>/plans/T<step-id>-plan.md` (one file per `tasks.md` step). They are authored at execution time, not up front. See the `tasks.md` template below.

Do not start `design.md` until `requirements.md` is agreed. Do not start `tasks.md` until `design.md` is agreed. Do not write code until `tasks.md` is agreed.

## Authoring ritual (applies to every spec document)

Before writing or editing any spec document, the agent must:

1. Read the existing state of the folder, the relevant code, and `AGENTS.md`.
2. Identify the decisions it would otherwise make by default.
3. Ask the user **5–7 short clarifying questions**.
   - **One decision = one question.** Don't bundle multiple choices into one question.
   - **Don't invent doubt.** If there is no real ambiguity, ask fewer questions (or none). Padding to hit a number produces noise and trains the user to ignore the ritual.
   - **Surface contradictions explicitly.** If the existing doc disagrees with itself, quote the conflicting lines.
   - Cite the source (file and line) when a question references existing material, so the user can verify quickly.
4. **Do not write the document until the user answers.** "Before you write anything" is the contract — honor it.
5. After answers come back, draft the document. Offer a diff/preview before applying further changes downstream.

## Document shape (informal templates)

These are starting templates, not strict schemas. Adapt to the problem at hand.

### `requirements.md`

- **Problem** — one short paragraph framing the gap and the change.
- **Example request / response** (when the problem is API-shaped) — concrete payloads.
- **Acceptance criteria** — one section per role/persona. Each AC is a single sentence in **EAR style** (see *Acceptance criteria — EAR style* below).
- **Out of scope** — bullets listing what is explicitly excluded so reviewers don't re-raise it.
- **Open questions** — anything that survived clarification and still needs a decision before `design.md` starts.

#### Acceptance criteria — EAR style

All acceptance criteria are written in **EAR (Easy Approach to Requirements Syntax)**. EAR keeps each criterion atomic, testable, and free of narrative drift. Pick the pattern that fits the behavior; don't mix patterns in one sentence.

| Pattern | Template | Use when |
| --- | --- | --- |
| Ubiquitous | "The system shall *response*." | Behavior is always true. |
| Event-driven | "When *trigger*, the system shall *response*." | Behavior is triggered by an event/request. |
| State-driven | "While *state*, the system shall *response*." | Behavior depends on an ongoing state. |
| Optional feature | "Where *feature*, the system shall *response*." | Behavior applies only when a feature is in scope. |
| Unwanted behavior | "If *unwanted trigger*, then the system shall *response*." | Error / rejection / fallback paths. |
| Complex | Combine two prefixes: "When *trigger*, while *state*, the system shall *response*." | Behavior depends on both a trigger and a state. |

Examples:

- *Ubiquitous* — "The system shall return `actor` as a structured object `{ id, type }` in every event."
- *Event-driven* — "When the client requests `size` greater than `500`, the system shall cap `size` at `500`."
- *Unwanted* — "If `from` is not a valid ISO-8601 instant, then the system shall return `400 Bad Request`."

Do not use Given/When/Then (BDD), free-form prose, or bulleted half-sentences for ACs — they degrade into ambiguity and slip past review. One EAR sentence = one AC.

### `design.md`

- **API contract** — endpoints, request/response, status codes.
- **Data model / migrations** — schema changes (Flyway), indexes, tiebreakers.
- **Validation rules** — per field, per endpoint.
- **Layer integration** — `controller / service / domain / persistence` touchpoints (see `AGENTS.md` § Architecture).
- **AGENTS.md alignment** — a small table mapping each relevant invariant to how this design honors it.

### `tasks.md`

- Numbered steps, each small enough to land as **one safe commit / one PR** (branch `<problem-name>/t<step-id>-<short-name>`, per `AGENTS.md` § PR invariants).
- Each step is independently verifiable: its own tests, its own migration if any, its own green build.
- **Refs.** Every step links back to the specific AC(s) in `requirements.md` and the design section(s) in `design.md` it implements. No orphan tasks (no AC ⇒ unjustified scope; no design ⇒ unplanned implementation).
- **Definition of Done.** Each step states its DoD as an explicit, testable bullet list — named tests that must pass, migrations that must run clean, behaviors that must be observable. "Looks done" and "code compiles" are not DoD.
- **Dependencies.** Each step lists which earlier step IDs it depends on (e.g. "depends on: 02, 04"). Steps with no prerequisites are explicitly marked independent so they can be parallelized.
- **Size.** One safe commit / one PR per step. If a step cannot be reverted by reverting a single commit, split it. If the diff spans unrelated layers without a single user-visible behavior change, split it.
- **Per-task execution plan.** Before writing code for a step, the executing agent drafts a plan at `.specs/<problem-name>/plans/T<step-id>-plan.md` (e.g. `.specs/query-api/plans/T02-plan.md` for step 02). The plan covers the concrete edits, file paths, test names, and any open questions surfaced during implementation — it is the working document for that single PR, scoped to one task. `tasks.md` stays the index; the per-task plans hold the detail. This is the spec-flow analog of the repo-root `PLAN.md` convention in `AGENTS.md` § Working with PLAN.md.
- The agent that executes a step appends a 1–3 line execution result at the end of that step in `tasks.md` (mirrors the `PLAN.md` convention in `AGENTS.md` § Working with PLAN.md). The corresponding `T<step-id>-plan.md` stays in the repo as history for that PR.

## Discipline

- The point of the ritual is to make defaults *visible* and let the user redirect them cheaply, instead of discovering them as bugs in `design.md` or, worse, in code.
- If the user says "skip the questions," skip them — but log the assumption you defaulted to in the doc itself.
- Specs are merged through normal PRs; the build-health and PR invariants in `AGENTS.md` apply to spec-only changes too (no commented-out blocks, no secrets, etc.).
