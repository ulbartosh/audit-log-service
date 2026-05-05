CREATE TABLE audit_events_archive (
    id          UUID        PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    resource    TEXT,
    outcome     TEXT        NOT NULL,
    context     JSONB,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
