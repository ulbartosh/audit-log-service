package com.training.bartosh.auditlog.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeysetPageResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omitsNextPageTokenWhenNull() throws Exception {
    KeysetPageResponse<String> response = new KeysetPageResponse<>(List.of("a", "b"), null);

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("items"));
    assertFalse(tree.has("nextPageToken"), "nextPageToken must be absent when null");
  }

  @Test
  void includesNextPageTokenWhenPresent() throws Exception {
    KeysetPageResponse<String> response =
        new KeysetPageResponse<>(List.of("a"), "opaque-token-string");

    JsonNode tree = mapper.readTree(mapper.writeValueAsString(response));

    assertTrue(tree.has("nextPageToken"));
    assertTrue(tree.get("nextPageToken").isTextual(), "nextPageToken must serialize as string");
    assertEquals("opaque-token-string", tree.get("nextPageToken").asText());
  }
}
