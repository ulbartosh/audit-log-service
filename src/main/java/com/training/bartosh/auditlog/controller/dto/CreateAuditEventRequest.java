package com.training.bartosh.auditlog.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.bartosh.auditlog.domain.Outcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAuditEventRequest(
    @Valid @NotNull ActorRequest actor,
    @NotBlank String action,
    @Valid ResourceRequest resource,
    Outcome outcome,
    JsonNode context,
    JsonNode payload) {}
