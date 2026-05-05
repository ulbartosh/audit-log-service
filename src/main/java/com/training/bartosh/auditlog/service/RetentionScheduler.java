package com.training.bartosh.auditlog.service;

import com.training.bartosh.auditlog.config.AuditLogProperties;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionScheduler {

  private final RetentionService retentionService;
  private final AuditLogProperties properties;

  public RetentionScheduler(RetentionService retentionService, AuditLogProperties properties) {
    this.retentionService = retentionService;
    this.properties = properties;
  }

  // 6-field Spring cron: second minute hour day-of-month month day-of-week
  @Scheduled(
      cron = "${auditlog.retention.cron:0 0 3 * * *}",
      zone = "${auditlog.retention.zone:UTC}")
  public void runRetention() {
    retentionService.archiveOlderThan(Duration.ofDays(properties.retention().days()));
  }
}
