package com.training.bartosh.auditlog.service;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

  private static final int BATCH_SIZE = 1000;
  private static final String INSERT_ARCHIVE_BATCH_SQL =
      """
      INSERT INTO audit_events_archive (
          id,
          occurred_at,
          actor,
          action,
          resource,
          outcome,
          context,
          archived_at
      )
      SELECT
          id,
          occurred_at,
          actor,
          action,
          resource,
          outcome,
          context,
          :archivedAt
      FROM audit_events
      WHERE occurred_at < :cutoff
        AND NOT EXISTS (
            SELECT 1 FROM audit_events_archive archive
            WHERE archive.id = audit_events.id
        )
      ORDER BY occurred_at ASC
      LIMIT :batchSize
      ON CONFLICT (id) DO NOTHING
      RETURNING id
      """;

  private final Clock clock;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final EntityManager entityManager;

  public RetentionService(
      Clock clock, NamedParameterJdbcTemplate jdbcTemplate, EntityManager entityManager) {
    this.clock = clock;
    this.jdbcTemplate = jdbcTemplate;
    this.entityManager = entityManager;
  }

  @Transactional
  public int archiveOlderThan(Duration retention) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(retention);
    Instant archivedAt = now;
    int total = 0;

    while (true) {
      List<UUID> ids =
          jdbcTemplate.query(
              INSERT_ARCHIVE_BATCH_SQL,
              new MapSqlParameterSource()
                  .addValue("cutoff", Timestamp.from(cutoff))
                  .addValue("archivedAt", Timestamp.from(archivedAt))
                  .addValue("batchSize", BATCH_SIZE),
              (rs, rowNum) -> rs.getObject("id", UUID.class));
      if (ids.isEmpty()) {
        return total;
      }

      // Native INSERT ... SELECT bypasses the persistence context; clear it so same-transaction
      // repository reads in tests and follow-on code observe the DB's post-archive state.
      entityManager.clear();
      total += ids.size();
      if (ids.size() < BATCH_SIZE) {
        return total;
      }
    }
  }
}
