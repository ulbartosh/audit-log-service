package com.training.bartosh.auditlog.persistence;

import com.training.bartosh.auditlog.domain.AuditEvent;

public final class AuditEventMapper {

  private AuditEventMapper() {}

  public static AuditEvent toDomain(AuditEventEntity entity) {
    return new AuditEvent(
        entity.getId(),
        entity.getOccurredAt(),
        entity.getActor(),
        entity.getAction(),
        entity.getResource(),
        entity.getOutcome(),
        entity.getContext());
  }

  public static AuditEventEntity toEntity(AuditEvent event) {
    return new AuditEventEntity(
        event.id(),
        event.occurredAt(),
        event.actor(),
        event.action(),
        event.resource(),
        event.outcome(),
        event.context());
  }
}
