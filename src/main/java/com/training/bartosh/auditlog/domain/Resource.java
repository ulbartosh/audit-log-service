package com.training.bartosh.auditlog.domain;

public record Resource(String id, String type) {

  public Resource {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("resource id is required");
    }
    if (type != null && type.isBlank()) {
      throw new IllegalArgumentException("resource type must be non-blank when present");
    }
  }
}
