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
 * All error responses share the shape <code>{"errors":[{"field"?,"message"}]}</code> so clients can
 * parse one structure regardless of failure mode. Field-level errors include {@code field};
 * non-validation errors omit it.
 *
 * <p>Inheriting from {@link ResponseEntityExceptionHandler} gives correct 4xx responses for the
 * Spring framework exceptions we don't handle explicitly (e.g. {@code
 * HttpMessageNotReadableException} for malformed JSON, {@code
 * HttpRequestMethodNotSupportedException} for wrong verbs). The catch-all {@code Exception} handler
 * below therefore only fires for genuine application errors.
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
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    String message = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
    return errorResponse(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
    log.error("Unhandled error", ex);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  private static ResponseEntity<Map<String, Object>> errorResponse(
      HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("errors", List.of(Map.of("message", message))));
  }
}
