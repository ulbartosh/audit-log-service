package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.Outcome;
import jakarta.validation.constraints.NotBlank;

public record CreateAuditEventRequest(
    @NotBlank String actor,
    @NotBlank String action,
    String resource,
    Outcome outcome,
    JsonNode context) {}
