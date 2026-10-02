package org.zalava.assistant.agent.domain;

import java.util.List;
import java.util.Objects;

public record AgentContext(
    String prompt,
    int sourceCount,
    int characterBudget,
    int charactersUsed,
    List<SourceMetric> sourceMetrics) {
  public AgentContext {
    prompt = prompt == null ? "" : prompt;
    if (sourceCount < 0
        || characterBudget < 0
        || charactersUsed < 0
        || charactersUsed > characterBudget) {
      throw new IllegalArgumentException("Invalid agent context bounds");
    }
    sourceMetrics =
        List.copyOf(Objects.requireNonNull(sourceMetrics, "sourceMetrics must not be null"));
    if (sourceCount != sourceMetrics.size()) {
      throw new IllegalArgumentException("sourceCount must match sourceMetrics size");
    }
  }

  public record SourceMetric(String sourceType, int charactersAvailable, int charactersUsed) {
    public SourceMetric {
      if (sourceType == null
          || sourceType.isBlank()
          || charactersAvailable < 0
          || charactersUsed < 0
          || charactersUsed > charactersAvailable) {
        throw new IllegalArgumentException("Invalid agent context source metric");
      }
    }
  }
}
