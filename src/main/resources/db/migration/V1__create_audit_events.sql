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

-- Append-only: block any UPDATE on the table at the DB layer.
CREATE RULE audit_events_no_update AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
-- DELETE is permitted but is reserved for the retention scheduler (phase C).
-- The functional "no DELETE" invariant is enforced in application code by
-- never exposing a delete operation through controller/service.
