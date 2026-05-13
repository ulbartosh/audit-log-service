package com.training.bartosh.auditlog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void illegalArgumentMapsToBadRequestErrorShape() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(Map.of("errors", List.of(Map.of("message", "bad input"))), response.getBody());
  }

  @Test
  void unknownExceptionMapsToInternalServerErrorShape() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleUnknown(new RuntimeException("boom"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals(
        Map.of("errors", List.of(Map.of("message", "Internal server error"))), response.getBody());
  }

  @Test
  void invalidPageTokenMapsToBadRequestWithFieldPageToken() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleInvalidPageToken(new InvalidPageTokenException("Invalid page token"));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(
        Map.of("errors", List.of(Map.of("field", "pageToken", "message", "Invalid page token"))),
        response.getBody());
  }

  @Test
  void invalidPageTokenWithNullMessageRendersDefaultMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleInvalidPageToken(new InvalidPageTokenException(null));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(
        Map.of("errors", List.of(Map.of("field", "pageToken", "message", "Invalid page token"))),
        response.getBody());
  }
}
