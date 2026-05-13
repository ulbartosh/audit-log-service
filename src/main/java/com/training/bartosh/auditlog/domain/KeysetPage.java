package com.training.bartosh.auditlog.domain;

import java.util.List;
import java.util.Optional;

public record KeysetPage<T>(List<T> items, Optional<Cursor> nextCursor) {

  public KeysetPage {
    if (items == null) {
      throw new IllegalArgumentException("items is required");
    }
    if (nextCursor == null) {
      throw new IllegalArgumentException("nextCursor is required");
    }
    items = List.copyOf(items);
  }
}
