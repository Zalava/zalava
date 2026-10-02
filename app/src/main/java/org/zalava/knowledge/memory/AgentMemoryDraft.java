package org.zalava.knowledge.memory;

import java.util.Map;
import java.util.Objects;

public record AgentMemoryDraft(AgentMemoryScope scope, String text, Map<String, String> metadata) {

  public AgentMemoryDraft {
    Objects.requireNonNull(scope, "scope must not be null");
    text = AgentMemory.requireText(text, "text");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
  }
}
