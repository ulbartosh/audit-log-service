# Query API — Requirements

## Problem

A read-only HTTP endpoint to retrieve stored audit events filtered by actor, resource, and time range, with pagination. Callers (compliance, SRE, security) need to distinguish identity *kind*, not just identity *string*, so `actor` and `resource` are exposed as structured `{ id, type }` objects on the wire. A new `payload` field carries event-specific structured data alongside the existing `context` field; the two have separate purposes and are independently optional.

## Example request

```
GET /audit-events
  ?actor=u_42,u_99
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

- The `actor` parameter accepts one to ten comma-separated actor IDs in a single query parameter.
- Each `actor` entry is trimmed of surrounding whitespace before validation and matching.
- The `actor` filter matches any listed actor `id` exactly (case-sensitive); `actor.type` is not a query filter in this iteration.
- Empty `actor` entries are invalid, including `actor=`, `actor=a1,,a2`, and `actor=a1,`.
- The maximum of ten `actor` entries is applied before de-duplicating repeated IDs.
- Duplicate actor IDs are accepted within the ten-entry limit and are de-duplicated after validation.
- Actor-list order and duplicates do not affect filter identity; `actor=a1,a2`, `actor=a2,a1`, and `actor=a1,a1,a2` are equivalent filters.
- The `resource` parameter matches the resource's `id` exactly (case-sensitive); `resource.type` is not a query filter in this iteration.
- The `from` and `to` parameters are **inclusive** bounds — events with `occurredAt == from` or `occurredAt == to` are returned.
- All provided filters combine with logical AND.

## Acceptance criteria

### US-1 Compliance officer — confirm or refute an action during an audit

- When the client sends `GET /audit-events` with `actor` containing comma-separated IDs, the system shall return only events whose actor `id` matches any listed ID.
- When the client sends `GET /audit-events` with `actor` entries containing surrounding whitespace, the system shall trim the entries before matching actor IDs.
- When the client sends `GET /audit-events` with duplicate actor IDs, the system shall treat each repeated actor ID as a single filter value.
- When the client sends `GET /audit-events` with `resource`, the system shall return only events whose resource `id` matches that resource.
- When the client sends `GET /audit-events` with `from`, the system shall return only events whose `occurredAt` is greater than or equal to `from`.
- When the client sends `GET /audit-events` with `to`, the system shall return only events whose `occurredAt` is less than or equal to `to`.
- When the client sends `GET /audit-events` with more than one filter type, the system shall combine all provided filters with logical AND.
- The system shall return `actor` as a structured object `{ id, type }` in every event.
- The system shall return `resource` as a structured object `{ id, type }` in every event.
- When the matched event set is empty, the system shall return `200 OK` with `"items": []` and `nextPageToken` omitted.
- If `actor` contains an empty entry, then the system shall return `400 Bad Request`.
- If `actor` contains more than ten comma-separated entries before de-duplication, then the system shall return `400 Bad Request`.
- If `from` is not a valid ISO-8601 instant, then the system shall return `400 Bad Request`.
- If `to` is not a valid ISO-8601 instant, then the system shall return `400 Bad Request`.
- If `from` is after `to`, then the system shall return `400 Bad Request`.

### US-2 SRE — reconstruct the timeline of actions on a resource during an incident

- The system shall return matched events most-recent-first (newest `occurredAt` returned before older ones).
- When an event has a `payload`, the system shall include the field in that event's response object.
- When an event has no `payload`, the system shall omit the field from that event's response object (the field shall not appear as `null`).
- When an event has a `context`, the system shall include the field in that event's response object.
- When an event has no `context`, the system shall omit the field from that event's response object.

### US-3 Security analyst — paginate a large result set without loss or duplication

- The system shall support paginated retrieval of result sets larger than a single response can carry.
- The system shall cap any single response at `500` events, regardless of any client-requested page size.
- The system shall guarantee that consecutive pages requested with identical filters return each matching row exactly once across the pagination session.
- When consecutive pages are requested with the same actor ID set in a different order, the system shall treat the actor filter as identical for pagination consistency.
- When the client requests a page beyond the end of the result set, the system shall return `200 OK` with `"items": []`.

## Out of scope

- Filtering by `actor.type` or `resource.type`.
- Repeated `actor` query parameters such as `?actor=a1&actor=a2`; only a single comma-separated `actor` parameter is in scope.
- Full-text search, partial matching, or case-insensitive matching for `actor` or `resource`.
- Returning a total result count or offset-style page numbers.
- Filtering or indexing by `context` or `payload` contents.
- Backfilling or rewriting existing audit event rows.
- Retention/archive behavior changes.

## Open questions

- None. Resolved decisions are recorded in [`design.md` → Decisions resolved from `requirements.md` open questions](./design.md#decisions-resolved-from-requirementsmd-open-questions).
