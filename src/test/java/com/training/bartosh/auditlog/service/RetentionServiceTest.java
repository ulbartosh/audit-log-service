package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

  private static final Instant FIXED = Instant.parse("2026-05-05T12:00:00Z");

  @Mock private NamedParameterJdbcTemplate jdbcTemplate;

  private RetentionService service;

  @BeforeEach
  void setUp() {
    service = new RetentionService(Clock.fixed(FIXED, ZoneOffset.UTC), jdbcTemplate);
  }

  @Test
  void returnsZeroWhenNoRowsNeedArchiving() {
    when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(0);

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(0, archived);
  }

  @Test
  void countsArchivedBatch() {
    when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(2);

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(2, archived);
  }

  @Test
  void continuesWhenAFullBatchWasArchived() {
    when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
        .thenReturn(1000)
        .thenReturn(1);

    int archived = service.archiveOlderThan(Duration.ofDays(365));

    assertEquals(1001, archived);
  }
}
