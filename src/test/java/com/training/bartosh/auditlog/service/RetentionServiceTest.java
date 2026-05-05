package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

  private static final Instant FIXED = Instant.parse("2026-05-05T12:00:00Z");

  @Mock private NamedParameterJdbcTemplate jdbcTemplate;
  @Mock private EntityManager entityManager;

  private RetentionService service;

  @BeforeEach
  void setUp() {
    service = new RetentionService(Clock.fixed(FIXED, ZoneOffset.UTC), jdbcTemplate, entityManager);
  }

  @Test
  void returnsZeroWhenNoRowsNeedArchiving() {
    when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), rowMapper()))
        .thenReturn(List.of());

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(0, archived);
    verify(entityManager, never()).clear();
  }

  @Test
  void countsArchivedBatchAndClearsPersistenceContext() {
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), rowMapper()))
        .thenReturn(ids);

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(2, archived);
    verify(entityManager).clear();
  }

  @Test
  void continuesWhenAFullBatchWasArchived() {
    List<UUID> firstBatch = java.util.stream.Stream.generate(UUID::randomUUID).limit(1000).toList();
    List<UUID> secondBatch = List.of(UUID.randomUUID());
    when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), rowMapper()))
        .thenReturn(firstBatch)
        .thenReturn(secondBatch);

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(1001, archived);
    verify(entityManager, org.mockito.Mockito.times(2)).clear();
  }

  @SuppressWarnings("unchecked")
  private static RowMapper<UUID> rowMapper() {
    return any(RowMapper.class);
  }
}
