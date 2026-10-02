package org.zalava.assistant.agent;

import java.util.List;
import java.util.Objects;

public record AgentContextAssembly(
    String prompt,
    int sourceCount,
    int characterBudget,
    int charactersUsed,
    List<SourceMetric> sourceMetrics) {

  public AgentContextAssembly {
    prompt = prompt == null ? "" : prompt;
    if (sourceCount < 0) {
      throw new IllegalArgumentException("sourceCount must not be negative");
    }
    if (characterBudget < 0) {
      throw new IllegalArgumentException("characterBudget must not be negative");
    }
    if (charactersUsed < 0) {
      throw new IllegalArgumentException("charactersUsed must not be negative");
    }
    if (charactersUsed > characterBudget) {
      throw new IllegalArgumentException("charactersUsed must not exceed characterBudget");
    }
    sourceMetrics =
        List.copyOf(Objects.requireNonNull(sourceMetrics, "sourceMetrics must not be null"));
    if (sourceCount != sourceMetrics.size()) {
      throw new IllegalArgumentException("sourceCount must match sourceMetrics size");
    }
  }

  public record SourceMetric(String sourceType, int charactersAvailable, int charactersUsed) {

    public SourceMetric {
      sourceType = requireText(sourceType, "sourceType");
      if (charactersAvailable < 0) {
        throw new IllegalArgumentException("charactersAvailable must not be negative");
      }
      if (charactersUsed < 0) {
        throw new IllegalArgumentException("charactersUsed must not be negative");
      }
      if (charactersUsed > charactersAvailable) {
        throw new IllegalArgumentException("charactersUsed must not exceed charactersAvailable");
      }
    }
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
