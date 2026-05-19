package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import com.training.bartosh.auditlog.domain.ActorType;
import com.training.bartosh.auditlog.domain.Outcome;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class AuditEventSpecificationsIT extends AuditLogIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");
  private static final Instant T1 = Instant.parse("2026-05-01T11:00:00Z");
  private static final Instant T2 = Instant.parse("2026-05-01T12:00:00Z");

  @Autowired private AuditEventRepository repository;
  @Autowired private EntityManager em;

  @Test
  void afterCursorReturnsRowsWithStrictlyEarlierOccurredAt() {
    UUID idAtT0 = UUID.randomUUID();
    UUID idAtT1 = UUID.randomUUID();
    UUID idAtT2 = UUID.randomUUID();
    seed(idAtT0, T0);
    seed(idAtT1, T1);
    seed(idAtT2, T2);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T2, idAtT2),
                PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
                        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID))))
            .getContent();

    assertEquals(2, result.size(), "T0 and T1 rows should be returned");
    assertEquals(idAtT1, result.get(0).getId(), "T1 first (DESC order)");
    assertEquals(idAtT0, result.get(1).getId(), "T0 second");
  }

  @Test
  void afterCursorTiebreakerReturnsRowsWithSameOccurredAtAndSmallerId() {
    UUID smaller = uuidWithMostSignificantBits(1L);
    UUID cursor = uuidWithMostSignificantBits(2L);
    UUID larger = uuidWithMostSignificantBits(3L);
    seed(smaller, T1);
    seed(cursor, T1);
    seed(larger, T1);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T1, cursor),
                PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
                        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID))))
            .getContent();

    assertEquals(1, result.size(), "only the row with id < cursor at the same instant returns");
    assertEquals(smaller, result.get(0).getId());
  }

  @Test
  void afterCursorExcludesCursorRowAndAllLaterRows() {
    UUID idAtT0 = UUID.randomUUID();
    UUID idAtT1 = UUID.randomUUID();
    UUID idAtT2 = UUID.randomUUID();
    seed(idAtT0, T0);
    seed(idAtT1, T1);
    seed(idAtT2, T2);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository
            .findAll(
                AuditEventSpecifications.afterCursor(T0, idAtT0),
                PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
                        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID))))
            .getContent();

    assertTrue(result.isEmpty(), "no rows are positioned before the earliest cursor");
  }

  @Test
  void byActorsReturnsRowsForAnyListedActor() {
    seed("a1", "project:42", T0);
    seed("a2", "project:42", T1);
    seed("a3", "project:42", T2);
    seed("other", "project:42", T2);
    em.flush();
    em.clear();

    List<AuditEventEntity> result =
        repository.findAll(AuditEventSpecifications.byActors(List.of("a1", "a2", "a3")), sort());

    assertEquals(Set.of("a1", "a2", "a3"), actorIds(result));
  }

  @Test
  void byActorsComposesWithResourceAndTimeRangePredicates() {
    seed("a1", "project:42", T1);
    seed("a2", "project:42", T2);
    seed("a1", "project:99", T1);
    seed("other", "project:42", T1);
    seed("a2", "project:42", T0);
    em.flush();
    em.clear();

    Specification<AuditEventEntity> spec =
        Specification.allOf(
            AuditEventSpecifications.byActors(List.of("a1", "a2")),
            AuditEventSpecifications.byResource("project:42"),
            AuditEventSpecifications.occurredAtOrAfter(T1),
            AuditEventSpecifications.occurredAtOrBefore(T2));

    List<AuditEventEntity> result = repository.findAll(spec, sort());

    assertEquals(2, result.size());
    assertEquals(Set.of("a1", "a2"), actorIds(result));
    assertTrue(result.stream().allMatch(row -> "project:42".equals(row.getResource())));
  }

  private void seed(UUID id, Instant occurredAt) {
    seed(id, "u-" + id, null, occurredAt);
  }

  private void seed(String actor, String resource, Instant occurredAt) {
    seed(UUID.randomUUID(), actor, resource, occurredAt);
  }

  private void seed(UUID id, String actor, String resource, Instant occurredAt) {
    repository.save(
        new AuditEventEntity(
            id,
            occurredAt,
            actor,
            ActorType.USER,
            "user.login",
            resource,
            null,
            Outcome.SUCCESS,
            null,
            null));
  }

  private static Sort sort() {
    return Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
        .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID));
  }

  private static Set<String> actorIds(List<AuditEventEntity> rows) {
    return rows.stream()
        .map(AuditEventEntity::getActor)
        .collect(java.util.stream.Collectors.toSet());
  }

  private static UUID uuidWithMostSignificantBits(long msb) {
    return new UUID(msb, 0L);
  }
}
