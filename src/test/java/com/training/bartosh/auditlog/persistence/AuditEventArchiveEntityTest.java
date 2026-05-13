package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.training.bartosh.auditlog.domain.ActorType;
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
    JsonNode payload = JsonNodeFactory.instance.objectNode().put("amount", 100);

    AuditEventArchiveEntity entity =
        new AuditEventArchiveEntity(
            id,
            occurredAt,
            "alice",
            ActorType.USER,
            "user.login",
            null,
            "project",
            Outcome.SUCCESS,
            null,
            payload,
            archivedAt);

    assertEquals(id, entity.getId());
    assertEquals(occurredAt, entity.getOccurredAt());
    assertEquals("alice", entity.getActor());
    assertEquals(ActorType.USER, entity.getActorType());
    assertEquals("user.login", entity.getAction());
    assertNull(entity.getResource());
    assertEquals("project", entity.getResourceType());
    assertEquals(Outcome.SUCCESS, entity.getOutcome());
    assertNull(entity.getContext());
    assertEquals(payload, entity.getPayload());
    assertEquals(archivedAt, entity.getArchivedAt());
  }
}
