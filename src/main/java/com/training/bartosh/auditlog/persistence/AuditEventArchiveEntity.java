package com.training.bartosh.auditlog.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.Outcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events_archive")
public class AuditEventArchiveEntity {

  @Id private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private String actor;

  @Column(nullable = false)
  private String action;

  @Column private String resource;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Outcome outcome;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode context;

  @Column(name = "archived_at", nullable = false)
  private Instant archivedAt;

  protected AuditEventArchiveEntity() {}

  public AuditEventArchiveEntity(
      UUID id,
      Instant occurredAt,
      String actor,
      String action,
      String resource,
      Outcome outcome,
      JsonNode context,
      Instant archivedAt) {
    this.id = id;
    this.occurredAt = occurredAt;
    this.actor = actor;
    this.action = action;
    this.resource = resource;
    this.outcome = outcome;
    this.context = context;
    this.archivedAt = archivedAt;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getActor() {
    return actor;
  }

  public String getAction() {
    return action;
  }

  public String getResource() {
    return resource;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public JsonNode getContext() {
    return context;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
