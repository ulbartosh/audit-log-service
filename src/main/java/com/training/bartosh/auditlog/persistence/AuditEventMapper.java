package com.training.bartosh.auditlog.persistence;

import com.training.bartosh.auditlog.domain.Actor;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.Resource;

public final class AuditEventMapper {

  private AuditEventMapper() {}

  public static AuditEvent toDomain(AuditEventEntity entity) {
    Actor actor = new Actor(entity.getActor(), entity.getActorType());
    Resource resource =
        entity.getResource() == null
            ? null
            : new Resource(entity.getResource(), entity.getResourceType());
    return new AuditEvent(
        entity.getId(),
        entity.getOccurredAt(),
        actor,
        entity.getAction(),
        resource,
        entity.getOutcome(),
        entity.getContext(),
        entity.getPayload());
  }

  public static AuditEventEntity toEntity(AuditEvent event) {
    return new AuditEventEntity(
        event.id(),
        event.occurredAt(),
        event.actor().id(),
        event.actor().type(),
        event.action(),
        event.resource() == null ? null : event.resource().id(),
        event.resource() == null ? null : event.resource().type(),
        event.outcome(),
        event.context(),
        event.payload());
  }
}
