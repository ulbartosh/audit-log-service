package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeysetPageResponse<T>(List<T> items, String nextPageToken) {

  public KeysetPageResponse {
    if (items == null) {
      throw new IllegalArgumentException("items must not be null");
    }
    items = List.copyOf(items);
  }
}
