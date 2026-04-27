package com.training.bartosh.auditlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AuditLogApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuditLogApplication.class, args);
  }
}
