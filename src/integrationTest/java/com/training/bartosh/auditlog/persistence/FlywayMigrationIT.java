package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FlywayMigrationIT extends AuditLogIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  void auditEventsTableExists() throws Exception {
    try (Connection c = dataSource.getConnection();
        ResultSet rs =
            c.getMetaData().getTables(null, "public", "audit_events", new String[] {"TABLE"})) {
      assertTrue(rs.next(), "audit_events table should exist");
    }
  }

  @Test
  void expectedIndexesExist() throws Exception {
    Set<String> indexes = new HashSet<>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement stmt =
            c.prepareStatement(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'audit_events'");
        ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        indexes.add(rs.getString(1));
      }
    }
    assertTrue(indexes.contains("idx_audit_events_actor_time"), "actor_time index missing");
    assertTrue(indexes.contains("idx_audit_events_resource_time"), "resource_time index missing");
    assertTrue(indexes.contains("idx_audit_events_time"), "time index missing");
  }
}
