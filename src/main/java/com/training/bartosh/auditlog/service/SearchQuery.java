package com.training.bartosh.auditlog.service;

import com.training.bartosh.auditlog.domain.Cursor;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SearchQuery(
    List<String> actorIds,
    String resource,
    Instant from,
    Instant to,
    Optional<Cursor> cursor,
    int size) {

  public SearchQuery {
    Objects.requireNonNull(actorIds, "actorIds must not be null");
    actorIds = List.copyOf(actorIds);
    if (actorIds.stream().anyMatch(actorId -> actorId == null || actorId.isBlank())) {
      throw new IllegalArgumentException("actorIds must contain only non-blank values");
    }
    if (resource != null && resource.isBlank()) {
      throw new IllegalArgumentException("resource must be non-blank when present");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must not be after to");
    }
    if (cursor == null) {
      throw new IllegalArgumentException("cursor must not be null (use Optional.empty())");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be >= 1");
    }
  }
}
