package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.bartosh.auditlog.domain.Actor;
import com.training.bartosh.auditlog.domain.ActorType;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.KeysetPage;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.domain.Outcome;
import com.training.bartosh.auditlog.domain.Resource;
import com.training.bartosh.auditlog.persistence.AuditEventEntity;
import com.training.bartosh.auditlog.persistence.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

  private static final Instant FIXED = Instant.parse("2026-04-27T12:00:00Z");
  private static final Actor ACTOR = new Actor("u1", ActorType.USER);

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
        service.record(new NewAuditEvent(ACTOR, "user.login", null, Outcome.SUCCESS, null, null));

    assertEquals(FIXED, saved.occurredAt());
  }

  @Test
  void recordGeneratesId() {
    when(repository.save(any(AuditEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    AuditEvent saved =
        service.record(new NewAuditEvent(ACTOR, "user.login", null, Outcome.SUCCESS, null, null));

    assertNotNull(saved.id());
  }

  @Test
  void recordPersistsExactlyTheClockTimestamp() {
    ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    Resource resource = new Resource("project:42", "project");

    service.record(new NewAuditEvent(ACTOR, "user.login", resource, Outcome.SUCCESS, null, null));

    AuditEventEntity persisted = captor.getValue();
    assertEquals(FIXED, persisted.getOccurredAt());
    assertEquals("u1", persisted.getActor());
    assertEquals(ActorType.USER, persisted.getActorType());
    assertEquals("project:42", persisted.getResource());
    assertEquals("project", persisted.getResourceType());
    verify(repository).save(any(AuditEventEntity.class));
  }

  @Test
  void searchFetchesSizePlusOneRowsForKeysetWindow() {
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findAll(specification(), captor.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    service.search(new SearchQuery(null, null, null, null, Optional.empty(), 10));

    assertEquals(11, captor.getValue().getPageSize(), "service must over-fetch by one");
  }

  @Test
  void searchOmitsNextCursorWhenRepositoryReturnsExactlySize() {
    when(repository.findAll(specification(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(entities(10)));

    KeysetPage<AuditEvent> page =
        service.search(new SearchQuery(null, null, null, null, Optional.empty(), 10));

    assertEquals(10, page.items().size());
    assertTrue(page.nextCursor().isEmpty());
  }

  @Test
  void searchReturnsNextCursorBuiltFromLastInRangeRow() {
    List<AuditEventEntity> rows = entities(11);
    AuditEventEntity tenth = rows.get(9);
    when(repository.findAll(specification(), any(Pageable.class))).thenReturn(new PageImpl<>(rows));

    KeysetPage<AuditEvent> page =
        service.search(new SearchQuery(null, null, null, null, Optional.empty(), 10));

    assertEquals(10, page.items().size());
    assertTrue(page.nextCursor().isPresent());
    assertEquals(tenth.getOccurredAt(), page.nextCursor().orElseThrow().occurredAt());
    assertEquals(tenth.getId(), page.nextCursor().orElseThrow().id());
  }

  private static List<AuditEventEntity> entities(int count) {
    List<AuditEventEntity> entities = new ArrayList<>();
    Instant base = Instant.parse("2026-05-01T12:00:00Z");
    for (int i = 0; i < count; i++) {
      entities.add(
          new AuditEventEntity(
              UUID.randomUUID(),
              base.minusSeconds(i),
              "u" + i,
              ActorType.USER,
              "user.login",
              null,
              null,
              Outcome.SUCCESS,
              null,
              null));
    }
    return entities;
  }

  @SuppressWarnings("unchecked")
  private static Specification<AuditEventEntity> specification() {
    return any(Specification.class);
  }
}
