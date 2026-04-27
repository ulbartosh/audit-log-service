package com.training.bartosh.auditlog.service;

import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.persistence.AuditEventEntity;
import com.training.bartosh.auditlog.persistence.AuditEventMapper;
import com.training.bartosh.auditlog.persistence.AuditEventRepository;
import com.training.bartosh.auditlog.persistence.AuditEventSpecifications;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            input.context());
    AuditEventEntity saved = repository.save(AuditEventMapper.toEntity(event));
    return AuditEventMapper.toDomain(saved);
  }

  @Transactional(readOnly = true)
  public Page<AuditEvent> search(SearchQuery query, Pageable pageable) {
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
    Specification<AuditEventEntity> spec = Specification.allOf(specs);
    return repository.findAll(spec, pageable).map(AuditEventMapper::toDomain);
  }
}
