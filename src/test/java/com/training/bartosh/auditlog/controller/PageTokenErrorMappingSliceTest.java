package com.training.bartosh.auditlog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PageTokenSliceController.class)
class PageTokenErrorMappingSliceTest {

  @Autowired private MockMvc mvc;

  @Test
  void invalidPageTokenRendersAs400WithFieldPageToken() throws Exception {
    mvc.perform(get("/__test/throw-page-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("pageToken"))
        .andExpect(jsonPath("$.errors[0].message").value("Invalid page token"));
  }
}
