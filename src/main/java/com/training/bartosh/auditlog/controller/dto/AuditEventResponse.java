package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.Outcome;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventResponse(
    UUID id,
    Instant occurredAt,
    ActorResponse actor,
    String action,
    ResourceResponse resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {

  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.id(),
        event.occurredAt(),
        new ActorResponse(event.actor().id(), event.actor().type()),
        event.action(),
        event.resource() == null
            ? null
            : new ResourceResponse(event.resource().id(), event.resource().type()),
        event.outcome(),
        event.context(),
        event.payload());
  }
}
