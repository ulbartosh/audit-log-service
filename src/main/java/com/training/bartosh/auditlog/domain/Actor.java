package com.training.bartosh.auditlog.domain;

public record Actor(String id, ActorType type) {

  public Actor {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("actor id is required");
    }
    if (type == null) {
      throw new IllegalArgumentException("actor type is required");
    }
  }
}
