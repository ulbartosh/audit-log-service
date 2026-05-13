package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.Actor;
import com.training.bartosh.auditlog.domain.ActorType;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.domain.Outcome;
import com.training.bartosh.auditlog.domain.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAuditEventRequest(
    @Valid @NotNull ActorRequest actor,
    @NotBlank String action,
    @Valid ResourceRequest resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {

  public NewAuditEvent toDomain() {
    Actor domainActor = new Actor(actor.id(), actor.type() != null ? actor.type() : ActorType.USER);
    Resource domainResource =
        resource == null ? null : new Resource(resource.id(), resource.type());
    return new NewAuditEvent(domainActor, action, domainResource, outcome, context, payload);
  }
}
