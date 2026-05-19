package com.training.bartosh.auditlog.controller;

import com.training.bartosh.auditlog.controller.dto.AuditEventResponse;
import com.training.bartosh.auditlog.controller.dto.CreateAuditEventRequest;
import com.training.bartosh.auditlog.controller.dto.KeysetPageResponse;
import com.training.bartosh.auditlog.domain.AuditEvent;
import com.training.bartosh.auditlog.domain.Cursor;
import com.training.bartosh.auditlog.domain.KeysetPage;
import com.training.bartosh.auditlog.domain.NewAuditEvent;
import com.training.bartosh.auditlog.service.AuditEventService;
import com.training.bartosh.auditlog.service.SearchQuery;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
  private final PageTokenCodec pageTokenCodec;
  private final ActorFilterParser actorFilterParser;

  public AuditEventController(
      AuditEventService service,
      PageTokenCodec pageTokenCodec,
      ActorFilterParser actorFilterParser) {
    this.service = service;
    this.pageTokenCodec = pageTokenCodec;
    this.actorFilterParser = actorFilterParser;
  }

  @PostMapping
  public ResponseEntity<AuditEventResponse> create(
      @Valid @RequestBody CreateAuditEventRequest req) {
    NewAuditEvent input = req.toDomain();
    AuditEvent event = service.record(input);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(event.id())
            .toUri();
    return ResponseEntity.created(location).body(AuditEventResponse.from(event));
  }

  @GetMapping
  public KeysetPageResponse<AuditEventResponse> search(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String resource,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) Optional<String> pageToken,
      @RequestParam(defaultValue = "50") int size) {

    if (size < 1) {
      throw new IllegalArgumentException("size must be >= 1");
    }
    int cappedSize = Math.min(size, MAX_PAGE_SIZE);
    Optional<Cursor> cursor = pageToken.map(pageTokenCodec::decode);
    List<String> actorIds = actorFilterParser.parse(actor);
    KeysetPage<AuditEvent> result =
        service.search(new SearchQuery(actorIds, resource, from, to, cursor, cappedSize));

    List<AuditEventResponse> items = result.items().stream().map(AuditEventResponse::from).toList();
    String nextToken = pageTokenCodec.encode(result.nextCursor().orElse(null));
    return new KeysetPageResponse<>(items, nextToken);
  }
}
