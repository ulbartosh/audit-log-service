package com.training.bartosh.auditlog;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiIT extends AuditLogIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void apiDocsExposeAuditEventsEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Audit Log Service"))
        .andExpect(jsonPath("$.paths.['/audit-events']").exists())
        .andExpect(jsonPath("$.paths.['/audit-events'].post").exists())
        .andExpect(jsonPath("$.paths.['/audit-events'].get").exists());
  }

  @Test
  void swaggerUiHtmlIsServed() throws Exception {
    mvc.perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Swagger UI")));
  }
}
