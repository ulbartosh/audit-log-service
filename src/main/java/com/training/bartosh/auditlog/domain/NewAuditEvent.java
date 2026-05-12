package com.training.bartosh.auditlog.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record NewAuditEvent(
    Actor actor,
    String action,
    Resource resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {

  public NewAuditEvent {
    if (actor == null) {
      throw new IllegalArgumentException("actor is required");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action is required");
    }
    if (outcome == null) {
      outcome = Outcome.SUCCESS;
    }
  }
}
