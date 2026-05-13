package com.training.bartosh.auditlog.controller.dto;

import com.training.bartosh.auditlog.domain.ActorType;
import jakarta.validation.constraints.NotBlank;

public record ActorRequest(@NotBlank String id, ActorType type) {}
