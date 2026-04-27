package com.training.bartosh.auditlog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

  private static final UUID ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-04-27T12:00:00Z");

  @Test
  void rejectsNullActor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, null, "user.login", null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsBlankActor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, "   ", "user.login", null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsNullAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, "u1", null, null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsBlankAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, "u1", "", null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsNullId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(null, NOW, "u1", "user.login", null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsNullOccurredAt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, null, "u1", "user.login", null, Outcome.SUCCESS, null));
  }

  @Test
  void rejectsNullOutcome() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, NOW, "u1", "user.login", null, null, null));
  }

  @Test
  void acceptsAllRequiredFields() {
    AuditEvent event =
        new AuditEvent(ID, NOW, "u1", "user.login", "project:42", Outcome.SUCCESS, null);
    assertEquals("u1", event.actor());
    assertEquals("user.login", event.action());
  }

  @Test
  void newAuditEventDefaultsOutcomeToSuccess() {
    NewAuditEvent input = new NewAuditEvent("u1", "user.login", null, null, null);
    assertEquals(Outcome.SUCCESS, input.outcome());
  }

  @Test
  void newAuditEventRejectsBlankActor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent(" ", "user.login", null, Outcome.SUCCESS, null));
  }

  @Test
  void newAuditEventRejectsBlankAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent("u1", "", null, Outcome.SUCCESS, null));
  }
}
