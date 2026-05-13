package com.training.bartosh.auditlog.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.bartosh.auditlog.AuditLogIntegrationTest;
import com.training.bartosh.auditlog.domain.Cursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuditEventControllerIT extends AuditLogIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private PageTokenCodec pageTokenCodec;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void postCreatesEvent() throws Exception {
    String body =
        """
        {
          "actor": {"id": "u1", "type": "USER"},
          "action": "user.login",
          "resource": {"id": "project:42", "type": "project"},
          "outcome": "SUCCESS"
        }
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.actor.id", equalTo("u1")))
        .andExpect(jsonPath("$.actor.type", equalTo("USER")))
        .andExpect(jsonPath("$.action", equalTo("user.login")))
        .andExpect(jsonPath("$.resource.id", equalTo("project:42")))
        .andExpect(jsonPath("$.resource.type", equalTo("project")))
        .andExpect(jsonPath("$.outcome", equalTo("SUCCESS")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.occurredAt").exists());
  }

  @Test
  void postRoundTripsStructuredActorAndResourceAndPayload() throws Exception {
    String body =
        """
        {
          "actor": {"id": "u1", "type": "USER"},
          "action": "payment.refunded",
          "resource": {"id": "order/42", "type": "order"},
          "outcome": "SUCCESS",
          "context": {"ip": "10.0.0.1"},
          "payload": {"amount": 100}
        }
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.actor.id", equalTo("u1")))
        .andExpect(jsonPath("$.actor.type", equalTo("USER")))
        .andExpect(jsonPath("$.resource.id", equalTo("order/42")))
        .andExpect(jsonPath("$.resource.type", equalTo("order")))
        .andExpect(jsonPath("$.context.ip", equalTo("10.0.0.1")))
        .andExpect(jsonPath("$.payload.amount", equalTo(100)));
  }

  @Test
  void postDefaultsActorTypeToUser() throws Exception {
    String body =
        """
        {"actor":{"id":"u1"},"action":"user.login","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.actor.type", equalTo("USER")));
  }

  @Test
  void postRejectsMissingActor() throws Exception {
    String body = """
        {"action":"user.login","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("actor")));
  }

  @Test
  void postRejectsActorIdBlank() throws Exception {
    String body =
        """
        {"actor":{"id":"  "},"action":"user.login","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("actor.id")));
  }

  @Test
  void postRejectsResourceIdBlankWhenResourcePresent() throws Exception {
    String body =
        """
        {"actor":{"id":"u1"},"action":"user.login","resource":{"id":""},"outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("resource.id")));
  }

  @Test
  void postRejectsResourceTypeBlankWhenResourcePresent() throws Exception {
    String body =
        """
        {
          "actor": {"id": "u1"},
          "action": "user.login",
          "resource": {"id": "project:42", "type": "  "},
          "outcome": "SUCCESS"
        }
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("resource type")));
  }

  @Test
  void postIgnoresClientSuppliedTimestamp() throws Exception {
    String pastTimestamp = "1999-01-01T00:00:00Z";
    String body =
        """
        {
          "actor": {"id": "u1"},
          "action": "user.login",
          "outcome": "SUCCESS",
          "timestamp": "%s",
          "occurredAt": "%s"
        }
        """
            .formatted(pastTimestamp, pastTimestamp);

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.occurredAt", org.hamcrest.Matchers.not(equalTo(pastTimestamp))));
  }

  @Test
  void postDefaultsOutcomeToSuccessWhenOmitted() throws Exception {
    String body = """
        {"actor":{"id":"u1"},"action":"user.login"}
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
        .andExpect(jsonPath("$.items[0].actor.id", equalTo("alice")))
        .andExpect(jsonPath("$.items[1].actor.id", equalTo("alice")));
  }

  @Test
  void getFiltersByResource() throws Exception {
    seed("u1", "user.login", "SUCCESS", "project:42");
    seed("u2", "user.login", "SUCCESS", "project:99");

    mvc.perform(get("/audit-events").param("resource", "project:42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].resource.id", equalTo("project:42")));
  }

  @Test
  void getFiltersByTimeRange() throws Exception {
    seed("u1", "user.login", "SUCCESS");
    seed("u1", "user.logout", "SUCCESS");

    String from = Instant.now().minusSeconds(60).toString();
    String to = Instant.now().plusSeconds(60).toString();

    mvc.perform(get("/audit-events").param("from", from).param("to", to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)));
  }

  @Test
  void getCombinesActorResourceAndTimeFilters() throws Exception {
    seed("combined-user", "project.updated", "SUCCESS", "project:42");
    seed("combined-user", "project.updated", "SUCCESS", "project:99");
    seed("other-user", "project.updated", "SUCCESS", "project:42");

    String from = Instant.now().minusSeconds(60).toString();
    String to = Instant.now().plusSeconds(60).toString();

    mvc.perform(
            get("/audit-events")
                .param("actor", "combined-user")
                .param("resource", "project:42")
                .param("from", from)
                .param("to", to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].actor.id", equalTo("combined-user")))
        .andExpect(jsonPath("$.items[0].resource.id", equalTo("project:42")));
  }

  @Test
  void getRespectsSizeRequestWhenLessThanCap() throws Exception {
    for (int i = 0; i < 3; i++) {
      seed("page-user", "user.login", "SUCCESS");
    }

    mvc.perform(get("/audit-events").param("actor", "page-user").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.size").doesNotExist())
        .andExpect(jsonPath("$.total").doesNotExist())
        .andExpect(jsonPath("$.nextPageToken").exists());
  }

  @Test
  void nullableFieldsOmittedFromResponse() throws Exception {
    String body =
        """
        {"actor":{"id":"u1"},"action":"user.login","outcome":"SUCCESS"}
        """;

    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.resource").doesNotExist())
        .andExpect(jsonPath("$.context").doesNotExist())
        .andExpect(jsonPath("$.payload").doesNotExist());
  }

  @Test
  void getReturnsEmptyResultWith200AndNoNextPageToken() throws Exception {
    mvc.perform(get("/audit-events").param("actor", "nobody-matches"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", is(empty())))
        .andExpect(jsonPath("$.nextPageToken").doesNotExist());
  }

  @Test
  void getRejectsFromAfterTo() throws Exception {
    mvc.perform(
            get("/audit-events")
                .param("from", "2026-01-02T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("from must not be after to")));
  }

  @Test
  void getRejectsMalformedFrom() throws Exception {
    mvc.perform(get("/audit-events").param("from", "not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("from")));
  }

  @Test
  void getRejectsMalformedTo() throws Exception {
    mvc.perform(get("/audit-events").param("to", "not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("to")));
  }

  @Test
  void getRejectsBlankActor() throws Exception {
    mvc.perform(get("/audit-events").param("actor", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("actor")));
  }

  @Test
  void getRejectsBlankResource() throws Exception {
    mvc.perform(get("/audit-events").param("resource", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("resource")));
  }

  @Test
  void getRejectsInvalidSize() throws Exception {
    mvc.perform(get("/audit-events").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("size must be >= 1")));

    mvc.perform(get("/audit-events").param("size", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("size must be >= 1")));

    mvc.perform(get("/audit-events").param("size", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("size")));
  }

  @Test
  void getReturnsEventsMostRecentFirst() throws Exception {
    seed("order-user", "user.login", "SUCCESS");
    Thread.sleep(2);
    seed("order-user", "user.login", "SUCCESS");
    Thread.sleep(2);
    seed("order-user", "user.login", "SUCCESS");

    MvcResult result =
        mvc.perform(get("/audit-events").param("actor", "order-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(3)))
            .andReturn();

    JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
    Instant t0 = Instant.parse(tree.get("items").get(0).get("occurredAt").asText());
    Instant t1 = Instant.parse(tree.get("items").get(1).get("occurredAt").asText());
    Instant t2 = Instant.parse(tree.get("items").get(2).get("occurredAt").asText());
    assertFalse(t0.isBefore(t1), "items[0] should be at or after items[1]");
    assertFalse(t1.isBefore(t2), "items[1] should be at or after items[2]");
  }

  @Test
  void getWalksMultiplePages() throws Exception {
    Set<String> seededIds = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      seededIds.add(seedAndReturnId("walk-user", "user.login", "SUCCESS"));
      Thread.sleep(2);
    }

    Set<String> collected = new HashSet<>();
    String token = null;
    int itemCount = 0;
    int safety = 10;
    while (safety-- > 0) {
      var req = get("/audit-events").param("actor", "walk-user").param("size", "2");
      if (token != null) {
        req = req.param("pageToken", token);
      }
      MvcResult result = mvc.perform(req).andExpect(status().isOk()).andReturn();
      JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
      for (JsonNode item : tree.get("items")) {
        collected.add(item.get("id").asText());
        itemCount++;
      }
      JsonNode tokenNode = tree.get("nextPageToken");
      if (tokenNode == null || tokenNode.isNull()) {
        break;
      }
      token = tokenNode.asText();
    }

    assertEquals(seededIds, collected, "walk should yield every seeded id, no duplicates");
    assertEquals(seededIds.size(), itemCount, "walk must not repeat any seeded id");
  }

  @Test
  void getCapsSizeAt500EvenWhenLarger() throws Exception {
    for (int i = 0; i < 501; i++) {
      seed("cap-user", "user.login", "SUCCESS");
    }

    mvc.perform(get("/audit-events").param("actor", "cap-user").param("size", "10000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(500)))
        .andExpect(jsonPath("$.nextPageToken").exists());
  }

  @Test
  void getIsStableUnderConcurrentInsert() throws Exception {
    String idA = seedAndReturnId("stable-user", "user.login", "SUCCESS");
    Thread.sleep(2);
    String idB = seedAndReturnId("stable-user", "user.login", "SUCCESS");
    Thread.sleep(2);
    String idC = seedAndReturnId("stable-user", "user.login", "SUCCESS");

    MvcResult page1 =
        mvc.perform(get("/audit-events").param("actor", "stable-user").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andReturn();
    JsonNode page1Tree = objectMapper.readTree(page1.getResponse().getContentAsString());
    Set<String> page1Ids = collectIds(page1Tree);
    String token = page1Tree.get("nextPageToken").asText();

    Thread.sleep(2);
    String idD = seedAndReturnId("stable-user", "user.login", "SUCCESS");

    MvcResult page2 =
        mvc.perform(
                get("/audit-events")
                    .param("actor", "stable-user")
                    .param("size", "2")
                    .param("pageToken", token))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode page2Tree = objectMapper.readTree(page2.getResponse().getContentAsString());
    Set<String> page2Ids = collectIds(page2Tree);

    Set<String> union = new HashSet<>(page1Ids);
    union.addAll(page2Ids);
    assertEquals(
        page1Ids.size() + page2Ids.size(), union.size(), "consecutive pages must not overlap");
    assertEquals(Set.of(idA, idB, idC), union, "walk must cover the originally seeded set");
    assertFalse(union.contains(idD), "new row inserted mid-walk must not appear on either page");
  }

  @Test
  void getReturnsEmptyForCursorBeyondEnd() throws Exception {
    seed("beyond-user", "user.login", "SUCCESS");

    Cursor beforeEverything = new Cursor(Instant.parse("1990-01-01T00:00:00Z"), new UUID(0L, 0L));
    String token = pageTokenCodec.encode(beforeEverything);

    mvc.perform(get("/audit-events").param("actor", "beyond-user").param("pageToken", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", is(empty())))
        .andExpect(jsonPath("$.nextPageToken").doesNotExist());
  }

  @Test
  void getRejectsMalformedPageToken() throws Exception {
    mvc.perform(get("/audit-events").param("pageToken", "!!!not-base64!!!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field", equalTo("pageToken")));

    String unsupportedVersion =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"v\":2,\"occurredAt\":\"2026-04-17T11:02:14.123Z\","
                        + "\"id\":\"00000000-0000-0000-0000-000000000001\"}")
                    .getBytes(StandardCharsets.UTF_8));
    mvc.perform(get("/audit-events").param("pageToken", unsupportedVersion))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field", equalTo("pageToken")));
  }

  private void seed(String actor, String action, String outcome) throws Exception {
    seed(actor, action, outcome, null);
  }

  private void seed(String actor, String action, String outcome, String resource) throws Exception {
    String resourceFragment =
        resource == null ? "" : ",\"resource\":{\"id\":\"%s\"}".formatted(resource);
    String body =
        """
        {"actor":{"id":"%s"},"action":"%s","outcome":"%s"%s}
        """
            .formatted(actor, action, outcome, resourceFragment);
    mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }

  private String seedAndReturnId(String actor, String action, String outcome) throws Exception {
    String body =
        """
        {"actor":{"id":"%s"},"action":"%s","outcome":"%s"}
        """
            .formatted(actor, action, outcome);
    MvcResult result =
        mvc.perform(post("/audit-events").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
    return tree.get("id").asText();
  }

  private static Set<String> collectIds(JsonNode tree) {
    Set<String> ids = new HashSet<>();
    tree.get("items").forEach(node -> ids.add(node.get("id").asText()));
    return ids;
  }
}
