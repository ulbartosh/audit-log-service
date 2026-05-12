package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ResourceResponse(String id, @JsonInclude(JsonInclude.Include.NON_NULL) String type) {}
