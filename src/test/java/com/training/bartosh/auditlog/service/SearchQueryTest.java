package com.training.bartosh.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchQueryTest {

  @Test
  void rejectsNullActorList() {
    assertThrows(
        NullPointerException.class,
        () -> new SearchQuery(null, null, null, null, Optional.empty(), 10));
  }

  @Test
  void rejectsBlankActorId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchQuery(List.of("a1", "  "), null, null, null, Optional.empty(), 10));
  }

  @Test
  void defensivelyCopiesActorIds() {
    List<String> mutableActorIds = new ArrayList<>(List.of("a1"));

    SearchQuery query = new SearchQuery(mutableActorIds, null, null, null, Optional.empty(), 10);
    mutableActorIds.add("a2");

    assertEquals(List.of("a1"), query.actorIds());
    assertThrows(UnsupportedOperationException.class, () -> query.actorIds().add("a3"));
  }
}
