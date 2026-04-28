package com.training.bartosh.auditlog.service;

import java.time.Instant;

public record SearchQuery(String actor, String resource, Instant from, Instant to) {}
