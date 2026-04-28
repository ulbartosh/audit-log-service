package com.training.bartosh.auditlog.persistence;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Property references go through the generated {@link AuditEventEntity_} JPA Metamodel so a future
 * field rename in {@link AuditEventEntity} fails compilation here, not at runtime.
 */
public final class AuditEventSpecifications {

  private AuditEventSpecifications() {}

  public static Specification<AuditEventEntity> byActor(String actor) {
    return (root, query, cb) -> cb.equal(root.get(AuditEventEntity_.actor), actor);
  }

  public static Specification<AuditEventEntity> byResource(String resource) {
    return (root, query, cb) -> cb.equal(root.get(AuditEventEntity_.resource), resource);
  }

  public static Specification<AuditEventEntity> occurredAtOrAfter(Instant from) {
    return (root, query, cb) ->
        cb.greaterThanOrEqualTo(root.get(AuditEventEntity_.occurredAt), from);
  }

  public static Specification<AuditEventEntity> occurredAtOrBefore(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(AuditEventEntity_.occurredAt), to);
  }
}
