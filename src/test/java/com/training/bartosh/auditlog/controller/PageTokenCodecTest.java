package com.training.bartosh.auditlog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.training.bartosh.auditlog.domain.Cursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageTokenCodecTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-04-17T11:02:14.123Z");
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final PageTokenCodec codec = new PageTokenCodec(mapper);

  @Test
  void roundTripsCursor() {
    Cursor original = new Cursor(OCCURRED_AT, ID);

    Cursor decoded = codec.decode(codec.encode(original));

    assertEquals(OCCURRED_AT, decoded.occurredAt());
    assertEquals(ID, decoded.id());
  }

  @Test
  void encoderOutputIsBase64UrlWithoutPadding() {
    String token = codec.encode(new Cursor(OCCURRED_AT, ID));

    assertFalse(token.contains("+"), "base64-url uses '-' instead of '+'");
    assertFalse(token.contains("/"), "base64-url uses '_' instead of '/'");
    assertFalse(token.contains("="), "encoder is configured withoutPadding()");
  }

  @Test
  void decodeRejectsNonBase64() {
    assertThrows(InvalidPageTokenException.class, () -> codec.decode("!!!not base64!!!"));
  }

  @Test
  void decodeRejectsMalformedJson() {
    String token = base64Url("{not json");

    assertThrows(InvalidPageTokenException.class, () -> codec.decode(token));
  }

  @Test
  void decodeRejectsMissingFields() {
    String token = base64Url("{\"v\":1}");

    assertThrows(InvalidPageTokenException.class, () -> codec.decode(token));
  }

  @Test
  void decodeRejectsUnsupportedVersion() {
    String token =
        base64Url("{\"v\":2,\"occurredAt\":\"" + OCCURRED_AT + "\",\"id\":\"" + ID + "\"}");

    assertThrows(InvalidPageTokenException.class, () -> codec.decode(token));
  }

  @Test
  void decodeRejectsNonIso8601OccurredAt() {
    String token = base64Url("{\"v\":1,\"occurredAt\":\"not-a-date\",\"id\":\"" + ID + "\"}");

    assertThrows(InvalidPageTokenException.class, () -> codec.decode(token));
  }

  @Test
  void decodeRejectsNonUuidId() {
    String token =
        base64Url("{\"v\":1,\"occurredAt\":\"" + OCCURRED_AT + "\",\"id\":\"not-a-uuid\"}");

    assertThrows(InvalidPageTokenException.class, () -> codec.decode(token));
  }

  private static String base64Url(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}
