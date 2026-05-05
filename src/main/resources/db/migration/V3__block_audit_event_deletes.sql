-- Strict append-only: deletes are blocked at the DB layer as well as updates.
CREATE RULE audit_events_no_delete AS ON DELETE TO audit_events DO INSTEAD NOTHING;
