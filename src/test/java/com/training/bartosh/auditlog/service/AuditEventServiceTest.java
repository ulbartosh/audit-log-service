package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.domain.Outcome;
import com.training.bartosh.auditlog.persistence.AuditEventEntity;
import com.training.bartosh.auditlog.persistence.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

  private static final Instant FIXED = Instant.parse("2026-04-27T12:00:00Z");

  @Mock private AuditEventRepository repository;

  private AuditEventService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new AuditEventService(repository, clock);
  }

  @Test
  void recordSetsTimestampFromClock() {
    when(repository.save(any(AuditEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    AuditEvent saved =
        service.record(new NewAuditEvent("u1", "user.login", null, Outcome.SUCCESS, null));

    assertEquals(FIXED, saved.occurredAt());
  }

  @Test
  void recordGeneratesId() {
    when(repository.save(any(AuditEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    AuditEvent saved =
        service.record(new NewAuditEvent("u1", "user.login", null, Outcome.SUCCESS, null));

    assertNotNull(saved.id());
  }

  @Test
  void recordPersistsExactlyTheClockTimestamp() {
    ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    service.record(new NewAuditEvent("u1", "user.login", "project:42", Outcome.SUCCESS, null));

    AuditEventEntity persisted = captor.getValue();
    assertEquals(FIXED, persisted.getOccurredAt());
    assertEquals("u1", persisted.getActor());
    assertEquals("project:42", persisted.getResource());
    verify(repository).save(any(AuditEventEntity.class));
  }
}
