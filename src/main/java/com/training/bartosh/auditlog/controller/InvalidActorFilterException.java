package com.training.bartosh.auditlog.controller;

public class InvalidActorFilterException extends RuntimeException {

  public InvalidActorFilterException(String message) {
    super(message);
  }

  public String field() {
    return "actor";
  }
}
