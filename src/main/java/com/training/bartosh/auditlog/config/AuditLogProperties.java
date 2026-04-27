package com.training.bartosh.auditlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auditlog")
public record AuditLogProperties(Retention retention) {

  public record Retention(int days) {}
}
