package com.training.bartosh.auditlog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

  private static final UUID ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-04-27T12:00:00Z");
  private static final Actor ACTOR = new Actor("u1", ActorType.USER);

  @Test
  void actorRejectsNullId() {
    assertThrows(IllegalArgumentException.class, () -> new Actor(null, ActorType.USER));
  }

  @Test
  void actorRejectsBlankId() {
    assertThrows(IllegalArgumentException.class, () -> new Actor("   ", ActorType.USER));
  }

  @Test
  void actorRejectsNullType() {
    assertThrows(IllegalArgumentException.class, () -> new Actor("u1", null));
  }

  @Test
  void resourceRejectsNullId() {
    assertThrows(IllegalArgumentException.class, () -> new Resource(null, null));
  }

  @Test
  void resourceRejectsBlankId() {
    assertThrows(IllegalArgumentException.class, () -> new Resource("   ", null));
  }

  @Test
  void resourceRejectsBlankType() {
    assertThrows(IllegalArgumentException.class, () -> new Resource("project:42", "   "));
  }

  @Test
  void resourceAcceptsNullType() {
    Resource resource = new Resource("project:42", null);
    assertEquals("project:42", resource.id());
  }

  @Test
  void rejectsNullActor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, null, "user.login", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void rejectsNullAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, ACTOR, null, null, Outcome.SUCCESS, null, null));
  }

  @Test
  void rejectsBlankAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, ACTOR, "", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void rejectsNullId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(null, NOW, ACTOR, "user.login", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void rejectsNullOccurredAt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, null, ACTOR, "user.login", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void rejectsNullOutcome() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, ACTOR, "user.login", null, null, null, null));
  }

  @Test
  void acceptsAllRequiredFields() {
    Resource resource = new Resource("project:42", "project");
    AuditEvent event =
        new AuditEvent(ID, NOW, ACTOR, "user.login", resource, Outcome.SUCCESS, null, null);
    assertEquals(ACTOR, event.actor());
    assertEquals("user.login", event.action());
  }

  @Test
  void auditEventCarriesPayload() {
    JsonNode payload = JsonNodeFactory.instance.objectNode().put("amount", 100);

    AuditEvent event =
        new AuditEvent(ID, NOW, ACTOR, "user.login", null, Outcome.SUCCESS, null, payload);

    assertEquals(payload, event.payload());
  }

  @Test
  void newAuditEventDefaultsOutcomeToSuccess() {
    NewAuditEvent input = new NewAuditEvent(ACTOR, "user.login", null, null, null, null);
    assertEquals(Outcome.SUCCESS, input.outcome());
  }

  @Test
  void newAuditEventRejectsNullActor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent(null, "user.login", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void newAuditEventRejectsBlankAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent(ACTOR, "", null, Outcome.SUCCESS, null, null));
  }

  @Test
  void newAuditEventCarriesPayload() {
    JsonNode payload = JsonNodeFactory.instance.objectNode().put("amount", 100);

    NewAuditEvent input =
        new NewAuditEvent(ACTOR, "user.login", null, Outcome.SUCCESS, null, payload);

    assertEquals(payload, input.payload());
  }
}
