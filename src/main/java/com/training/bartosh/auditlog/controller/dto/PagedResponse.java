package com.training.bartosh.auditlog.controller.dto;

import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int size, long total) {}
