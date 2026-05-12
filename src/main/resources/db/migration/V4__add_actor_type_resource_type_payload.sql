ALTER TABLE audit_events
    ADD COLUMN actor_type    TEXT  NOT NULL DEFAULT 'USER',
    ADD COLUMN resource_type TEXT,
    ADD COLUMN payload       JSONB;

ALTER TABLE audit_events_archive
    ADD COLUMN actor_type    TEXT  NOT NULL DEFAULT 'USER',
    ADD COLUMN resource_type TEXT,
    ADD COLUMN payload       JSONB;

DROP INDEX idx_audit_events_actor_time;
DROP INDEX idx_audit_events_resource_time;
DROP INDEX idx_audit_events_time;

CREATE INDEX idx_audit_events_actor_time
    ON audit_events (actor, occurred_at DESC, id DESC);

CREATE INDEX idx_audit_events_resource_time
    ON audit_events (resource, occurred_at DESC, id DESC);

CREATE INDEX idx_audit_events_time
    ON audit_events (occurred_at DESC, id DESC);
