package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

  @Test
  void v4AddsNewColumnsToAuditEvents() throws Exception {
    Map<String, ColumnInfo> columns = loadColumns("audit_events");

    assertActorTypeColumn(columns.get("actor_type"));
    assertTextNullableColumn(columns.get("resource_type"), "resource_type");
    assertJsonbNullableColumn(columns.get("payload"), "payload");
  }

  @Test
  void v4AddsNewColumnsToAuditEventsArchive() throws Exception {
    Map<String, ColumnInfo> columns = loadColumns("audit_events_archive");

    assertActorTypeColumn(columns.get("actor_type"));
    assertTextNullableColumn(columns.get("resource_type"), "resource_type");
    assertJsonbNullableColumn(columns.get("payload"), "payload");
  }

  @Test
  void v4RecreatesIndexesWithIdTiebreaker() throws Exception {
    Map<String, String> indexes = loadIndexDefinitions();

    assertIndexDefinitionContains(
        indexes, "idx_audit_events_actor_time", "(actor, occurred_at DESC, id DESC)");
    assertIndexDefinitionContains(
        indexes, "idx_audit_events_resource_time", "(resource, occurred_at DESC, id DESC)");
    assertIndexDefinitionContains(indexes, "idx_audit_events_time", "(occurred_at DESC, id DESC)");
  }

  private Map<String, ColumnInfo> loadColumns(String tableName) throws Exception {
    Map<String, ColumnInfo> columns = new HashMap<>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement stmt =
            c.prepareStatement(
                """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name IN ('actor_type', 'resource_type', 'payload')
                """)) {
      stmt.setString(1, tableName);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          columns.put(
              rs.getString("column_name"),
              new ColumnInfo(
                  rs.getString("data_type"),
                  rs.getString("is_nullable"),
                  rs.getString("column_default")));
        }
      }
    }
    return columns;
  }

  private Map<String, String> loadIndexDefinitions() throws Exception {
    Map<String, String> indexes = new HashMap<>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement stmt =
            c.prepareStatement(
                """
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'audit_events'
                  AND indexname IN (
                    'idx_audit_events_actor_time',
                    'idx_audit_events_resource_time',
                    'idx_audit_events_time'
                  )
                """);
        ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        indexes.put(rs.getString("indexname"), rs.getString("indexdef"));
      }
    }
    return indexes;
  }

  private static void assertActorTypeColumn(ColumnInfo column) {
    assertNotNull(column, "actor_type column missing");
    assertEquals("text", column.dataType());
    assertEquals("NO", column.isNullable());
    assertNotNull(column.defaultValue(), "actor_type default missing");
    assertTrue(column.defaultValue().contains("'USER'::text"), "actor_type default should be USER");
  }

  private static void assertTextNullableColumn(ColumnInfo column, String columnName) {
    assertNotNull(column, columnName + " column missing");
    assertEquals("text", column.dataType());
    assertEquals("YES", column.isNullable());
  }

  private static void assertJsonbNullableColumn(ColumnInfo column, String columnName) {
    assertNotNull(column, columnName + " column missing");
    assertEquals("jsonb", column.dataType());
    assertEquals("YES", column.isNullable());
  }

  private static void assertIndexDefinitionContains(
      Map<String, String> indexes, String indexName, String expectedFragment) {
    String indexDef = indexes.get(indexName);
    assertNotNull(indexDef, indexName + " index missing");
    assertTrue(
        indexDef.contains(expectedFragment),
        () -> indexName + " should contain " + expectedFragment + " but was " + indexDef);
  }

  private record ColumnInfo(String dataType, String isNullable, String defaultValue) {}
}
