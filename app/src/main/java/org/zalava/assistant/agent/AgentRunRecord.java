package org.zalava.assistant.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AgentRunRecord(
    String id,
    String conversationId,
    PromptType promptType,
    String promptPreview,
    int selectedToolCount,
    int contextSourceCount,
    int contextCharacterBudget,
    int contextCharactersUsed,
    List<ContextSourceMetric> contextSourceMetrics,
    Instant startedAt,
    Instant completedAt,
    long durationMillis,
    Status status,
    String resultPreview,
    String errorPreview) {

  public static final int PREVIEW_LIMIT = 500;

  public AgentRunRecord {
    id = requireText(id, "id");
    conversationId = conversationId == null ? "" : conversationId;
    Objects.requireNonNull(promptType, "promptType must not be null");
    promptPreview = preview(promptPreview);
    if (selectedToolCount < 0) {
      throw new IllegalArgumentException("selectedToolCount must not be negative");
    }
    if (contextSourceCount < 0) {
      throw new IllegalArgumentException("contextSourceCount must not be negative");
    }
    if (contextCharacterBudget < 0) {
      throw new IllegalArgumentException("contextCharacterBudget must not be negative");
    }
    if (contextCharactersUsed < 0) {
      throw new IllegalArgumentException("contextCharactersUsed must not be negative");
    }
    if (contextCharactersUsed > contextCharacterBudget) {
      throw new IllegalArgumentException(
          "contextCharactersUsed must not exceed contextCharacterBudget");
    }
    contextSourceMetrics =
        List.copyOf(
            Objects.requireNonNull(contextSourceMetrics, "contextSourceMetrics must not be null"));
    if (contextSourceMetrics.size() != contextSourceCount) {
      throw new IllegalArgumentException("contextSourceMetrics size must match contextSourceCount");
    }
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(completedAt, "completedAt must not be null");
    if (durationMillis < 0) {
      throw new IllegalArgumentException("durationMillis must not be negative");
    }
    Objects.requireNonNull(status, "status must not be null");
    resultPreview = preview(resultPreview);
    errorPreview = preview(errorPreview);
  }

  public Duration duration() {
    return Duration.ofMillis(durationMillis);
  }

  public record ContextSourceMetric(
      String sourceType, int charactersAvailable, int charactersUsed) {

    public ContextSourceMetric {
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

    static ContextSourceMetric from(AgentContextAssembly.SourceMetric metric) {
      return new ContextSourceMetric(
          metric.sourceType(), metric.charactersAvailable(), metric.charactersUsed());
    }
  }

  static String preview(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    if (text.length() <= PREVIEW_LIMIT) {
      return text;
    }
    return text.substring(0, PREVIEW_LIMIT);
  }

  static String errorPreview(Throwable failure) {
    if (failure == null) {
      return null;
    }
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return preview(failure.getClass().getSimpleName());
    }
    return preview(failure.getClass().getSimpleName() + ": " + message);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public enum PromptType {
    CONVERSATIONAL,
    STRUCTURED
  }

  public enum Status {
    SUCCEEDED,
    FAILED
  }
}
