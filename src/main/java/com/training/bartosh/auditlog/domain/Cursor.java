package com.training.bartosh.auditlog.domain;

import java.time.Instant;
import java.util.UUID;

public record Cursor(Instant occurredAt, UUID id) {

  public Cursor {
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
  }
}
