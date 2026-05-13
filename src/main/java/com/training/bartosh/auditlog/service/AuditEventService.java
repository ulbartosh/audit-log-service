package com.training.bartosh.auditlog.service;

import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.Cursor;
import com.training.bartosh.auditlog.domain.KeysetPage;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.persistence.AuditEventEntity;
import com.training.bartosh.auditlog.persistence.AuditEventEntity_;
import com.training.bartosh.auditlog.persistence.AuditEventMapper;
import com.training.bartosh.auditlog.persistence.AuditEventRepository;
import com.training.bartosh.auditlog.persistence.AuditEventSpecifications;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

  private final AuditEventRepository repository;
  private final Clock clock;

  public AuditEventService(AuditEventRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public AuditEvent record(NewAuditEvent input) {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            clock.instant(),
            input.actor(),
            input.action(),
            input.resource(),
            input.outcome(),
            input.context(),
            input.payload());
    AuditEventEntity saved = repository.save(AuditEventMapper.toEntity(event));
    return AuditEventMapper.toDomain(saved);
  }

  @Transactional(readOnly = true)
  public KeysetPage<AuditEvent> search(SearchQuery query) {
    List<Specification<AuditEventEntity>> specs = new ArrayList<>();
    if (query.actor() != null) {
      specs.add(AuditEventSpecifications.byActor(query.actor()));
    }
    if (query.resource() != null) {
      specs.add(AuditEventSpecifications.byResource(query.resource()));
    }
    if (query.from() != null) {
      specs.add(AuditEventSpecifications.occurredAtOrAfter(query.from()));
    }
    if (query.to() != null) {
      specs.add(AuditEventSpecifications.occurredAtOrBefore(query.to()));
    }
    query
        .cursor()
        .ifPresent(c -> specs.add(AuditEventSpecifications.afterCursor(c.occurredAt(), c.id())));
    Specification<AuditEventEntity> spec = Specification.allOf(specs);

    Sort sort =
        Sort.by(Sort.Direction.DESC, AuditEventEntity_.OCCURRED_AT)
            .and(Sort.by(Sort.Direction.DESC, AuditEventEntity_.ID));

    List<AuditEventEntity> rows =
        repository.findAll(spec, PageRequest.of(0, query.size() + 1, sort)).getContent();

    if (rows.size() <= query.size()) {
      return new KeysetPage<>(
          rows.stream().map(AuditEventMapper::toDomain).toList(), Optional.empty());
    }
    List<AuditEventEntity> page = rows.subList(0, query.size());
    AuditEventEntity last = page.get(page.size() - 1);
    return new KeysetPage<>(
        page.stream().map(AuditEventMapper::toDomain).toList(),
        Optional.of(new Cursor(last.getOccurredAt(), last.getId())));
  }
}
