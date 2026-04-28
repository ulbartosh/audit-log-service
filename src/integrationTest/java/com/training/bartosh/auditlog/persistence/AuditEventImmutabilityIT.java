package com.training.bartosh.auditlog.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditEventImmutabilityIT extends AuditLogIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  void updateOnAuditEventsIsRejectedByDatabaseRule() throws Exception {
    UUID id = UUID.randomUUID();
    String originalActor = "alice";
    String tamperedActor = "tampered";

    try (Connection c = dataSource.getConnection()) {
      try (PreparedStatement ins =
          c.prepareStatement(
              "INSERT INTO audit_events (id, actor, action, outcome) VALUES (?, ?, ?, ?)")) {
        ins.setObject(1, id);
        ins.setString(2, originalActor);
        ins.setString(3, "user.login");
        ins.setString(4, "SUCCESS");
        assertEquals(1, ins.executeUpdate(), "INSERT should succeed");
      }

      try (PreparedStatement upd =
          c.prepareStatement("UPDATE audit_events SET actor = ? WHERE id = ?")) {
        upd.setString(1, tamperedActor);
        upd.setObject(2, id);
        assertEquals(
            0, upd.executeUpdate(), "UPDATE should be a no-op due to audit_events_no_update rule");
      }

      try (PreparedStatement sel =
              c.prepareStatement("SELECT actor FROM audit_events WHERE id = ?");
          ResultSet rs = selectActor(sel, id)) {
        assertTrue(rs.next(), "row must still exist after rejected UPDATE");
        assertEquals(originalActor, rs.getString("actor"), "actor must remain unchanged");
      }
    }
  }

  private static ResultSet selectActor(PreparedStatement sel, UUID id) throws Exception {
    sel.setObject(1, id);
    return sel.executeQuery();
  }
}
