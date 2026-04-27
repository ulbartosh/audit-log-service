package com.training.bartosh.auditlog.persistence;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public final class AuditEventSpecifications {

  private AuditEventSpecifications() {}

  public static Specification<AuditEventEntity> byActor(String actor) {
    return (root, query, cb) -> cb.equal(root.get("actor"), actor);
  }

  public static Specification<AuditEventEntity> byResource(String resource) {
    return (root, query, cb) -> cb.equal(root.get("resource"), resource);
  }

  public static Specification<AuditEventEntity> occurredAtOrAfter(Instant from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
  }

  public static Specification<AuditEventEntity> occurredAtOrBefore(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to);
  }
}
