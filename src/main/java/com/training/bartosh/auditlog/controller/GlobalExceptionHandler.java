package com.training.bartosh.auditlog.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Inheriting from {@link ResponseEntityExceptionHandler} gives correct 4xx responses for the Spring
 * framework exceptions we don't handle explicitly (e.g. {@code HttpMessageNotReadableException} for
 * malformed JSON, {@code HttpRequestMethodNotSupportedException} for wrong verbs). The catch-all
 * {@code Exception} handler below therefore only fires for genuine application errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(Map.of("errors", errors));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
    String message = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnknown(Exception ex) {
    log.error("Unhandled error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", "Internal server error"));
  }
}
