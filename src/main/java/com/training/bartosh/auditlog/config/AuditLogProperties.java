package com.training.bartosh.auditlog.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "auditlog")
public record AuditLogProperties(Retention retention) {

  // cron follows Spring's 6-field syntax: second minute hour day-of-month month day-of-week
  public record Retention(int days, String cron, String zone) {

    public Retention {
      if (days <= 0) {
        throw new IllegalArgumentException("auditlog.retention.days must be > 0");
      }
      if (cron == null || cron.isBlank()) {
        throw new IllegalArgumentException("auditlog.retention.cron must not be blank");
      }
      CronExpression.parse(cron);
      if (zone == null || zone.isBlank()) {
        throw new IllegalArgumentException("auditlog.retention.zone must not be blank");
      }
      ZoneId.of(zone);
    }
  }
}
