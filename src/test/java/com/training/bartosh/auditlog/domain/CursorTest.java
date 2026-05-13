package com.training.bartosh.auditlog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-04-17T11:02:14.123Z");
  private static final UUID ID = UUID.randomUUID();

  @Test
  void rejectsNullOccurredAt() {
    assertThrows(IllegalArgumentException.class, () -> new Cursor(null, ID));
  }

  @Test
  void rejectsNullId() {
    assertThrows(IllegalArgumentException.class, () -> new Cursor(OCCURRED_AT, null));
  }

  @Test
  void acceptsBothFields() {
    Cursor cursor = new Cursor(OCCURRED_AT, ID);

    assertEquals(OCCURRED_AT, cursor.occurredAt());
    assertEquals(ID, cursor.id());
  }
}
