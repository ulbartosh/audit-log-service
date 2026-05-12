package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import com.training.bartosh.auditlog.config.AuditLogProperties;
import com.training.bartosh.auditlog.domain.ActorType;
import com.training.bartosh.auditlog.domain.Outcome;
import com.training.bartosh.auditlog.persistence.AuditEventArchiveRepository;
import com.training.bartosh.auditlog.persistence.AuditEventEntity;
import com.training.bartosh.auditlog.persistence.AuditEventRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetentionServiceIT extends AuditLogIntegrationTest {

  @Autowired private RetentionService retentionService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private AuditEventArchiveRepository archiveRepository;
  @Autowired private AuditLogProperties properties;
  @Autowired private EntityManager em;

  @Test
  void oldEventsAreCopiedToArchiveAndRemainInMain() {
    auditEventRepository.saveAll(
        List.of(
            entity("actor-old", Instant.now().minus(400, ChronoUnit.DAYS)),
            entity("actor-young", Instant.now().minus(100, ChronoUnit.DAYS))));
    // Flush pending INSERTs to DB within the test transaction so the service
    // query can see them; clear the session so subsequent reads go to the DB.
    em.flush();
    em.clear();

    int archived = retentionService.archiveOlderThan(Duration.ofDays(365));

    assertEquals(1, archived);
    assertEquals(2, auditEventRepository.count());
    assertEquals(1, archiveRepository.count());
    var archivedEvent = archiveRepository.findAll().get(0);
    assertEquals("actor-old", archivedEvent.getActor());
    assertEquals(ActorType.USER, archivedEvent.getActorType());
    assertEquals("project:42", archivedEvent.getResource());
    assertEquals("project", archivedEvent.getResourceType());
    assertEquals("archived", archivedEvent.getPayload().get("kind").asText());
    assertNotNull(archivedEvent.getArchivedAt());
  }

  @Test
  void nothingArchivedWhenAllEventsAreYoung() {
    auditEventRepository.save(entity("actor1", Instant.now().minus(10, ChronoUnit.DAYS)));
    em.flush();
    em.clear();

    int archived = retentionService.archiveOlderThan(Duration.ofDays(365));

    assertEquals(0, archived);
    assertEquals(1, auditEventRepository.count());
    assertEquals(0, archiveRepository.count());
  }

  @Test
  void alreadyArchivedEventsAreNotCopiedTwice() {
    auditEventRepository.save(entity("actor-old", Instant.now().minus(400, ChronoUnit.DAYS)));
    em.flush();
    em.clear();

    assertEquals(1, retentionService.archiveOlderThan(Duration.ofDays(365)));
    assertEquals(0, retentionService.archiveOlderThan(Duration.ofDays(365)));
    assertEquals(1, auditEventRepository.count());
    assertEquals(1, archiveRepository.count());
  }

  @Test
  void defaultRetentionPropertiesAreCorrect() {
    assertEquals(365, properties.retention().days());
    assertEquals("0 0 3 * * *", properties.retention().cron());
    assertEquals("UTC", properties.retention().zone());
  }

  private static AuditEventEntity entity(String actor, Instant occurredAt) {
    JsonNode payload = JsonNodeFactory.instance.objectNode().put("kind", "archived");
    return new AuditEventEntity(
        UUID.randomUUID(),
        occurredAt,
        actor,
        ActorType.USER,
        "user.login",
        "project:42",
        "project",
        Outcome.SUCCESS,
        null,
        payload);
  }
}
