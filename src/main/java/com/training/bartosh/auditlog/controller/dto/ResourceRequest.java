package com.training.bartosh.auditlog.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ResourceRequest(@NotBlank String id, String type) {}
