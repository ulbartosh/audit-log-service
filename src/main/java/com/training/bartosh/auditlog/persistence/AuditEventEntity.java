package com.training.bartosh.auditlog.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.ActorType;
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
@Table(name = "audit_events")
public class AuditEventEntity {

  @Id private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private String actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false)
  private ActorType actorType;

  @Column(nullable = false)
  private String action;

  @Column private String resource;

  @Column(name = "resource_type")
  private String resourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Outcome outcome;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode context;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode payload;

  protected AuditEventEntity() {}

  public AuditEventEntity(
      UUID id,
      Instant occurredAt,
      String actor,
      ActorType actorType,
      String action,
      String resource,
      String resourceType,
      Outcome outcome,
      JsonNode context,
      JsonNode payload) {
    this.id = id;
    this.occurredAt = occurredAt;
    this.actor = actor;
    this.actorType = actorType;
    this.action = action;
    this.resource = resource;
    this.resourceType = resourceType;
    this.outcome = outcome;
    this.context = context;
    this.payload = payload;
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

  public ActorType getActorType() {
    return actorType;
  }

  public String getAction() {
    return action;
  }

  public String getResource() {
    return resource;
  }

  public String getResourceType() {
    return resourceType;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public JsonNode getContext() {
    return context;
  }

  public JsonNode getPayload() {
    return payload;
  }
}
