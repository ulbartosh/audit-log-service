package com.training.bartosh.auditlog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ActorFilterParserTest {

  private final ActorFilterParser parser = new ActorFilterParser();

  @Test
  void absentActorReturnsEmptyList() {
    assertEquals(List.of(), parser.parse(null));
  }

  @Test
  void oneActorReturnsOneActor() {
    assertEquals(List.of("a1"), parser.parse("a1"));
  }

  @Test
  void threeActorsReturnSortedUniqueActors() {
    assertEquals(List.of("a1", "a2", "a3"), parser.parse("a3,a1,a2"));
  }

  @Test
  void exactlyTenActorsAreAccepted() {
    assertEquals(
        List.of("a1", "a10", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"),
        parser.parse("a1,a2,a3,a4,a5,a6,a7,a8,a9,a10"));
  }

  @Test
  void trimsSurroundingWhitespace() {
    assertEquals(List.of("a1", "a2"), parser.parse(" a1 ,  a2 "));
  }

  @Test
  void duplicateValuesAreDeduplicated() {
    assertEquals(List.of("a1", "a2"), parser.parse("a1,a1,a2"));
  }

  @Test
  void actorValuesAreReturnedSorted() {
    assertEquals(List.of("a1", "a2", "a3"), parser.parse("a2,a3,a1"));
  }

  @Test
  void emptyActorValueIsRejected() {
    assertThrows(InvalidActorFilterException.class, () -> parser.parse(""));
  }

  @Test
  void emptyMiddleEntryIsRejected() {
    assertThrows(InvalidActorFilterException.class, () -> parser.parse("a1,,a2"));
  }

  @Test
  void emptyTrailingEntryIsRejected() {
    assertThrows(InvalidActorFilterException.class, () -> parser.parse("a1,"));
  }

  @Test
  void elevenRawValuesAreRejectedBeforeDeduplication() {
    assertThrows(
        InvalidActorFilterException.class, () -> parser.parse("a1,a1,a1,a1,a1,a1,a1,a1,a1,a1,a1"));
  }
}
