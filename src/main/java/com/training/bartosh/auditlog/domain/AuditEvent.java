package com.training.bartosh.auditlog.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID id,
    Instant occurredAt,
    String actor,
    String action,
    String resource,
    Outcome outcome,
    JsonNode context) {

  public AuditEvent {
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("actor is required");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action is required");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("outcome is required");
    }
  }
}
