package com.training.bartosh.auditlog.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeysetPageResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omitsNextPageTokenWhenNull() throws Exception {
    KeysetPageResponse<String> response = new KeysetPageResponse<>(List.of("a", "b"), null);

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("items"));
    assertEquals(2, tree.get("items").size());
    assertEquals("a", tree.get("items").get(0).asText());
    assertEquals("b", tree.get("items").get(1).asText());
    assertFalse(tree.has("nextPageToken"), "nextPageToken must be absent when null");
  }

  @Test
  void includesNextPageTokenWhenPresent() throws Exception {
    KeysetPageResponse<String> response =
        new KeysetPageResponse<>(List.of("a"), "opaque-token-string");

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("items"));
    assertEquals(1, tree.get("items").size());
    assertEquals("a", tree.get("items").get(0).asText());
    assertTrue(tree.has("nextPageToken"));
    assertTrue(tree.get("nextPageToken").isTextual(), "nextPageToken must serialize as string");
    assertEquals("opaque-token-string", tree.get("nextPageToken").asText());
  }

  @Test
  void rejectsNullItems() {
    assertThrows(
        IllegalArgumentException.class, () -> new KeysetPageResponse<String>(null, "token"));
  }

  @Test
  void itemsAreDefensivelyCopiedFromSourceList() {
    List<String> source = new ArrayList<>();
    source.add("a");

    KeysetPageResponse<String> response = new KeysetPageResponse<>(source, null);
    source.add("b");

    assertEquals(1, response.items().size());
    assertEquals("a", response.items().get(0));
  }

  @Test
  void rejectsNullElementInItems() {
    List<String> withNull = Arrays.asList("a", null);

    assertThrows(NullPointerException.class, () -> new KeysetPageResponse<>(withNull, null));
  }
}
