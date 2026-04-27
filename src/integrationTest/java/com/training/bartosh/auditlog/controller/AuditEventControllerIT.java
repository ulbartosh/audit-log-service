package com.training.bartosh.auditlog.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AuditEventControllerIT extends AuditLogIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void postCreatesEvent() throws Exception {
    String body =
        """
        {"actor":"u1","action":"user.login","resource":"project:42","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.actor", equalTo("u1")))
        .andExpect(jsonPath("$.action", equalTo("user.login")))
        .andExpect(jsonPath("$.resource", equalTo("project:42")))
        .andExpect(jsonPath("$.outcome", equalTo("SUCCESS")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.occurredAt").exists());
  }

  @Test
  void postRejectsMissingActor() throws Exception {
    String body = """
        {"action":"user.login","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").exists());
  }

  @Test
  void postIgnoresClientSuppliedTimestamp() throws Exception {
    String pastTimestamp = "1999-01-01T00:00:00Z";
    String body =
        """
        {"actor":"u1","action":"user.login","outcome":"SUCCESS","timestamp":"%s","occurredAt":"%s"}
        """
            .formatted(pastTimestamp, pastTimestamp);

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.occurredAt", org.hamcrest.Matchers.not(equalTo(pastTimestamp))));
  }

  @Test
  void postDefaultsOutcomeToSuccessWhenOmitted() throws Exception {
    String body = """
        {"actor":"u1","action":"user.login"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.outcome", equalTo("SUCCESS")));
  }

  @Test
  void getFiltersByActor() throws Exception {
    seed("alice", "user.login", "SUCCESS");
    seed("bob", "user.login", "SUCCESS");
    seed("alice", "user.logout", "SUCCESS");

    mvc.perform(get("/audit-events").param("actor", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].actor", equalTo("alice")))
        .andExpect(jsonPath("$.items[1].actor", equalTo("alice")));
  }

  @Test
  void getFiltersByResource() throws Exception {
    seed("u1", "user.login", "SUCCESS", "project:42");
    seed("u2", "user.login", "SUCCESS", "project:99");

    mvc.perform(get("/audit-events").param("resource", "project:42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].resource", equalTo("project:42")));
  }

  @Test
  void getFiltersByTimeRange() throws Exception {
    seed("u1", "user.login", "SUCCESS");
    seed("u1", "user.logout", "SUCCESS");

    String from = java.time.Instant.now().minusSeconds(60).toString();
    String to = java.time.Instant.now().plusSeconds(60).toString();

    mvc.perform(get("/audit-events").param("from", from).param("to", to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)));
  }

  @Test
  void getRespectsPaginationLimits() throws Exception {
    for (int i = 0; i < 3; i++) {
      seed("page-user", "user.login", "SUCCESS");
    }

    mvc.perform(get("/audit-events").param("actor", "page-user").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.size", equalTo(2)))
        .andExpect(jsonPath("$.total", equalTo(3)));
  }

  private void seed(String actor, String action, String outcome) throws Exception {
    seed(actor, action, outcome, null);
  }

  private void seed(String actor, String action, String outcome, String resource) throws Exception {
    String body =
        resource == null
            ? """
            {"actor":"%s","action":"%s","outcome":"%s"}
            """
                .formatted(actor, action, outcome)
            : """
            {"actor":"%s","action":"%s","outcome":"%s","resource":"%s"}
            """
                .formatted(actor, action, outcome, resource);
    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }
}
