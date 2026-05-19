package com.training.bartosh.auditlog.controller;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActorFilterParser {

  private static final int MAX_RAW_ACTOR_ENTRIES = 10;

  public List<String> parse(String rawActor) {
    if (rawActor == null) {
      return List.of();
    }

    String[] rawEntries = rawActor.split(",", -1);
    if (rawEntries.length > MAX_RAW_ACTOR_ENTRIES) {
      throw new InvalidActorFilterException(
          "actor must contain at most " + MAX_RAW_ACTOR_ENTRIES + " entries");
    }

    List<String> actorIds =
        Arrays.stream(rawEntries).map(String::trim).distinct().sorted().toList();
    if (actorIds.stream().anyMatch(String::isEmpty)) {
      throw new InvalidActorFilterException("actor entries must not be empty");
    }
    return actorIds;
  }
}
