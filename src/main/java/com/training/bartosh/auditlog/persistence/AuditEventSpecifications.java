package com.training.bartosh.auditlog.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Property references go through the generated {@link AuditEventEntity_} JPA Metamodel so a future
 * field rename in {@link AuditEventEntity} fails compilation here, not at runtime.
 */
public final class AuditEventSpecifications {

  private AuditEventSpecifications() {}

  public static Specification<AuditEventEntity> byActors(Collection<String> actorIds) {
    List<String> canonicalActorIds = List.copyOf(Objects.requireNonNull(actorIds));
    return (root, query, cb) -> root.get(AuditEventEntity_.actor).in(canonicalActorIds);
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

  public static Specification<AuditEventEntity> afterCursor(Instant ts, UUID lastId) {
    Objects.requireNonNull(ts, "ts must not be null");
    Objects.requireNonNull(lastId, "lastId must not be null");
    return (root, query, cb) ->
        cb.or(
            cb.lessThan(root.get(AuditEventEntity_.occurredAt), ts),
            cb.and(
                cb.equal(root.get(AuditEventEntity_.occurredAt), ts),
                cb.lessThan(root.get(AuditEventEntity_.id), lastId)));
  }
}
