package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.training.bartosh.auditlog.domain.Outcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventArchiveEntityTest {

  @Test
  void constructorExposesAllFieldsViaGetters() {
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-05-05T12:00:00Z");
    Instant archivedAt = Instant.parse("2026-05-06T03:00:00Z");

    AuditEventArchiveEntity entity =
        new AuditEventArchiveEntity(
            id, occurredAt, "alice", "user.login", null, Outcome.SUCCESS, null, archivedAt);

    assertEquals(id, entity.getId());
    assertEquals(occurredAt, entity.getOccurredAt());
    assertEquals("alice", entity.getActor());
    assertEquals("user.login", entity.getAction());
    assertNull(entity.getResource());
    assertEquals(Outcome.SUCCESS, entity.getOutcome());
    assertNull(entity.getContext());
    assertEquals(archivedAt, entity.getArchivedAt());
  }
}
