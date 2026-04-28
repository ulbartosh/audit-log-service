package com.training.bartosh.auditlog.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record NewAuditEvent(
    String actor, String action, String resource, Outcome outcome, JsonNode context) {

  public NewAuditEvent {
    if (actor == null || actor.isBlank()) {
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
