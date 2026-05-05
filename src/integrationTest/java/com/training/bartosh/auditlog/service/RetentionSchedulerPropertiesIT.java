package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.training.bartosh.auditlog.TestcontainersConfiguration;
import com.training.bartosh.auditlog.config.AuditLogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "auditlog.retention.days=30",
      "auditlog.retention.cron=0 15 2 * * *",
      "auditlog.retention.zone=Europe/Warsaw"
    })
@Import(TestcontainersConfiguration.class)
class RetentionSchedulerPropertiesIT {

  @Autowired private AuditLogProperties properties;
  @Autowired private RetentionScheduler retentionScheduler;

  @Test
  void customRetentionScheduleBindsAndBoots() {
    assertNotNull(retentionScheduler);
    assertEquals(30, properties.retention().days());
    assertEquals("0 15 2 * * *", properties.retention().cron());
    assertEquals("Europe/Warsaw", properties.retention().zone());
  }
}
