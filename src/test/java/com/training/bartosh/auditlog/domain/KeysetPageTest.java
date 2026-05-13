package com.training.bartosh.auditlog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeysetPageTest {

  private static final Cursor CURSOR =
      new Cursor(Instant.parse("2026-04-17T11:02:14.123Z"), UUID.randomUUID());

  @Test
  void rejectsNullItems() {
    assertThrows(
        IllegalArgumentException.class, () -> new KeysetPage<String>(null, Optional.empty()));
  }

  @Test
  void rejectsNullNextCursor() {
    assertThrows(IllegalArgumentException.class, () -> new KeysetPage<String>(List.of(), null));
  }

  @Test
  void acceptsEmptyItemsAndEmptyCursor() {
    KeysetPage<String> page = new KeysetPage<>(List.of(), Optional.empty());

    assertTrue(page.items().isEmpty());
    assertTrue(page.nextCursor().isEmpty());
  }

  @Test
  void acceptsItemsWithCursor() {
    KeysetPage<String> page = new KeysetPage<>(List.of("a", "b"), Optional.of(CURSOR));

    assertEquals(2, page.items().size());
    assertEquals(CURSOR, page.nextCursor().orElseThrow());
  }
}
