# Query API — Requirements

## Problem

A read-only HTTP endpoint to retrieve stored audit events filtered by actor, resource, and time range, with pagination. Callers (compliance, SRE, security) need to distinguish identity *kind*, not just identity *string*, so `actor` and `resource` are exposed as structured `{ id, type }` objects on the wire. A new `payload` field carries event-specific structured data alongside the existing `context` field; the two have separate purposes and are independently optional.

## Example request

```
GET /audit-events
  ?actor=u_42
  &resource=order/9f3b…
  &from=2026-04-01T00:00:00Z
  &to=2026-05-01T00:00:00Z
  &size=50
```

Subsequent pages add `&pageToken=<opaque>`; see `design.md` for the cursor shape.

## Example response

```json
{
  "items": [
    {
      "id": "01HE…Z9",
      "occurredAt": "2026-04-17T11:02:14Z",
      "actor":    { "id": "u_42",        "type": "USER"  },
      "resource": { "id": "order/9f3b…", "type": "order" },
      "action":   "order.refunded",
      "outcome":  "SUCCESS",
      "context":  { "ip": "10.0.0.1" },
      "payload":  { "amount": 100 }
    }
  ],
  "nextPageToken": "eyJ2IjoxLCJvY2N1cnJlZEF0Ijoi…"
}
```

`nextPageToken` is omitted when the current page exhausts the result set.

## Filter semantics

- The `actor` parameter matches the actor's `id` exactly (case-sensitive); `actor.type` is not a query filter in this iteration.
- The `resource` parameter matches the resource's `id` exactly (case-sensitive); `resource.type` is not a query filter in this iteration.
- The `from` and `to` parameters are **inclusive** bounds — events with `occurredAt == from` or `occurredAt == to` are returned.
- All provided filters combine with logical AND.

## Acceptance criteria

### Compliance officer — confirm or refute an action during an audit

- When the client sends `GET /audit-events` with any combination of `actor`, `resource`, `from`, `to`, the system shall return only the events matching all provided filters.
- The system shall return `actor` as a structured object `{ id, type }` in every event.
- The system shall return `resource` as a structured object `{ id, type }` in every event.
- When the matched event set is empty, the system shall return `200 OK` with `"items": []` and `nextPageToken` omitted.
- If `from` is not a valid ISO-8601 instant, then the system shall return `400 Bad Request`.
- If `to` is not a valid ISO-8601 instant, then the system shall return `400 Bad Request`.
- If `from` is after `to`, then the system shall return `400 Bad Request`.

### SRE — reconstruct the timeline of actions on a resource during an incident

- The system shall return matched events most-recent-first (newest `occurredAt` returned before older ones).
- When an event has a `payload`, the system shall include the field in that event's response object.
- When an event has no `payload`, the system shall omit the field from that event's response object (the field shall not appear as `null`).
- When an event has a `context`, the system shall include the field in that event's response object.
- When an event has no `context`, the system shall omit the field from that event's response object.

### Security analyst — paginate a large result set without loss or duplication

- The system shall support paginated retrieval of result sets larger than a single response can carry.
- The system shall cap any single response at `500` events, regardless of any client-requested page size.
- The system shall guarantee that consecutive pages requested with identical filters contain no overlapping rows and no missing rows.
- When the client requests a page beyond the end of the result set, the system shall return `200 OK` with `"items": []`.

## Out of scope

- Filtering by `action` or `outcome`.
- Filtering by `actor.type` or `resource.type`.
- Full-text or fuzzy search on `context` or `payload`.
- Multi-value filters (e.g., `actor=u_1&actor=u_2`).
- Sorting on any field other than `occurredAt`, or in any direction other than most-recent-first.
- Export to formats other than JSON.
- Changes to ingest *semantics* (validation beyond shape, side effects, retention). The `POST /audit-events` contract is updated only to reflect the new structured `actor` / `resource` and the new `payload` field; see `design.md`.
- AuthN / AuthZ.

## Open questions

- **`resource.type` shape.** Is `resource.type` a free-form string (`"order"`, `"invoice"`, …) or a constrained enum like `actor.type`? Assumption pending: free-form.
- **`resource.id` extraction.** Stored resources today look like `order/9f3b…` (a single string). How is that split into `{ id, type }` for the response — does `id` keep the full string or only the part after the separator? Resolve in `design.md`.
- **`actor.type` values.** Beyond `USER`, are other values (`SERVICE`, `SYSTEM`, …) anticipated in this iteration, or strictly `USER` only?
- **`payload` vs `context` convention.** Both fields are optional structured data with "different purposes"; the contract between ingester and reader needs a one-line documented convention (e.g., "`payload` is the canonical event body; `context` is environmental metadata") before the GET endpoint ships to production callers.
- **Legacy events.** Existing rows were written before `actor` and `resource` were structured. How are they represented in the response — synthesized `type` defaults, or a one-off backfill? Resolve in `design.md` (out of scope for `requirements.md` per "no changes to ingest").
- **Pagination strategy (keyset vs offset) and stability under concurrent ingest.** The no-overlap / no-missing-rows AC for the security analyst is not guaranteed by naive `LIMIT/OFFSET` against a live append-only table: new events arriving between page fetches shift row positions and can cause duplication at page boundaries, and non-unique sort keys can cause ties to flip. The wire-level pagination shape (`page`/`size`/`total` vs. next-page token) is consequently also unresolved. Resolve in `design.md` — options include (a) offset pagination with a stable composite sort `(occurredAt DESC, id DESC)` plus a pinned `to` ceiling per pagination session, (b) keyset/seek pagination with an opaque continuation token, or (c) keyset internally with a `page`/`size` façade on the wire. Whichever is chosen must satisfy the behavioral guarantees in the security-analyst section above.
- **Deterministic sort with tiebreaker.** `occurredAt` alone is not unique — two events can share a millisecond, and even ULID-generated timestamps can collide across writers. Without a stable tiebreaker, tie-resolution can flip between queries, breaking the no-overlap / no-missing-rows guarantee for the security analyst regardless of pagination strategy. Resolve in `design.md` — likely `(occurredAt DESC, id DESC)` since `id` is monotonic and unique, but the choice should be made explicitly.
- **Indexes for the filterable columns.** Filters combine `actor.id`, `resource.id`, and an `occurredAt` range, with results sorted by `occurredAt DESC` (plus tiebreaker). The query plan and required indexes — single-column, composite, partial, covering for the sort — affect both latency and the feasibility of keyset pagination. Resolve in `design.md`: enumerate the indexes the query path depends on, including any needed to support efficient `total` count if that requirement survives.

> Resolutions for all of the above are recorded in [`design.md` → Decisions resolved from `requirements.md` open questions](./design.md#decisions-resolved-from-requirementsmd-open-questions).
