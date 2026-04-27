package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.Outcome;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    Instant occurredAt,
    String actor,
    String action,
    String resource,
    Outcome outcome,
    JsonNode context) {

  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.occurredAt(),
        event.actor(),
        event.action(),
        event.resource(),
        event.outcome(),
        event.context());
  }
}
