package com.training.bartosh.auditlog.controller.dto;

import com.training.bartosh.auditlog.domain.ActorType;

public record ActorResponse(String id, ActorType type) {}
