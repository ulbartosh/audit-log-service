package com.training.bartosh.auditlog.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.zone.ZoneRulesException;
import org.junit.jupiter.api.Test;

class AuditLogPropertiesTest {

  @Test
  void acceptsValidRetentionConfiguration() {
    assertDoesNotThrow(() -> new AuditLogProperties.Retention(365, "0 0 3 * * *", "UTC"));
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditLogProperties.Retention(0, "0 0 3 * * *", "UTC"));
  }

  @Test
  void rejectsInvalidCronExpression() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditLogProperties.Retention(365, "not-a-cron", "UTC"));
  }

  @Test
  void rejectsInvalidTimeZone() {
    assertThrows(
        ZoneRulesException.class,
        () -> new AuditLogProperties.Retention(365, "0 0 3 * * *", "Mars/Olympus"));
  }
}
