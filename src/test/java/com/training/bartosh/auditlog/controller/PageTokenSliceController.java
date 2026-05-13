package com.training.bartosh.auditlog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PageTokenSliceController {

  @GetMapping("/__test/throw-page-token")
  public void throwIt() {
    throw new InvalidPageTokenException("Invalid page token");
  }
}
