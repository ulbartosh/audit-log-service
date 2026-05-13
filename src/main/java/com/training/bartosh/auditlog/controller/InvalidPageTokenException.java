package com.training.bartosh.auditlog.controller;

public class InvalidPageTokenException extends RuntimeException {

  private static final String FIELD = "pageToken";

  public InvalidPageTokenException(String message) {
    super(message);
  }

  public InvalidPageTokenException(String message, Throwable cause) {
    super(message, cause);
  }

  public String field() {
    return FIELD;
  }
}
