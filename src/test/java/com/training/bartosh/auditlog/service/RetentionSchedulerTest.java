package com.training.bartosh.auditlog.service;

import static org.mockito.Mockito.verify;

import com.training.bartosh.auditlog.config.AuditLogProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetentionSchedulerTest {

  @Mock private RetentionService retentionService;

  @Test
  void runRetentionUsesConfiguredRetentionDays() {
    AuditLogProperties properties =
        new AuditLogProperties(new AuditLogProperties.Retention(365, "0 0 3 * * *", "UTC"));
    RetentionScheduler scheduler = new RetentionScheduler(retentionService, properties);

    scheduler.runRetention();

    verify(retentionService).archiveOlderThan(Duration.ofDays(365));
  }
}
