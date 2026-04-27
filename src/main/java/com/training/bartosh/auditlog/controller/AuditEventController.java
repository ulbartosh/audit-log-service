package com.training.bartosh.auditlog.controller;

import com.training.bartosh.auditlog.controller.dto.AuditEventResponse;
import com.training.bartosh.auditlog.controller.dto.CreateAuditEventRequest;
import com.training.bartosh.auditlog.controller.dto.PagedResponse;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.service.AuditEventService;
import com.training.bartosh.auditlog.service.SearchQuery;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/audit-events")
public class AuditEventController {

  private static final int MAX_PAGE_SIZE = 500;

  private final AuditEventService service;

  public AuditEventController(AuditEventService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AuditEventResponse> create(
      @Valid @RequestBody CreateAuditEventRequest req) {
    NewAuditEvent input =
        new NewAuditEvent(req.actor(), req.action(), req.resource(), req.outcome(), req.context());
    AuditEvent event = service.record(input);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(event.id())
            .toUri();
    return ResponseEntity.created(location).body(AuditEventResponse.from(event));
  }

  @GetMapping
  public PagedResponse<AuditEventResponse> search(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String resource,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    PageRequest pageable =
        PageRequest.of(safePage, cappedSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
    Page<AuditEvent> result = service.search(new SearchQuery(actor, resource, from, to), pageable);
    return new PagedResponse<>(
        result.getContent().stream().map(AuditEventResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements());
  }
}
