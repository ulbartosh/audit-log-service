package com.training.bartosh.auditlog.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.bartosh.auditlog.domain.Cursor;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PageTokenCodec {

  private static final int VERSION = 1;
  private static final String INVALID = "Invalid page token";

  private final ObjectMapper mapper;

  public PageTokenCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String encode(Cursor cursor) {
    if (cursor == null) {
      return null;
    }
    try {
      byte[] json = mapper.writeValueAsBytes(new Token(VERSION, cursor.occurredAt(), cursor.id()));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to encode page token", e);
    }
  }

  public Cursor decode(String token) {
    if (token == null) {
      throw new InvalidPageTokenException(INVALID);
    }
    byte[] json;
    try {
      json = Base64.getUrlDecoder().decode(token);
    } catch (IllegalArgumentException e) {
      throw new InvalidPageTokenException(INVALID, e);
    }
    Token parsed;
    try {
      parsed = mapper.readValue(json, Token.class);
    } catch (IOException e) {
      throw new InvalidPageTokenException(INVALID, e);
    }
    if (parsed == null
        || parsed.v() != VERSION
        || parsed.occurredAt() == null
        || parsed.id() == null) {
      throw new InvalidPageTokenException(INVALID);
    }
    return new Cursor(parsed.occurredAt(), parsed.id());
  }

  private record Token(int v, Instant occurredAt, UUID id) {}
}
