package org.zalava.assistant.agent.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AgentRun(
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
    String errorPreview,
    int structuredAttemptCount,
    StructuredFailureCategory structuredFailureCategory) {
  public static final int PREVIEW_LIMIT = 500;

  public AgentRun {
    if (id == null
        || id.isBlank()
        || selectedToolCount < 0
        || contextSourceCount < 0
        || contextCharacterBudget < 0
        || contextCharactersUsed < 0
        || contextCharactersUsed > contextCharacterBudget
        || durationMillis < 0
        || structuredAttemptCount < 0) {
      throw new IllegalArgumentException("Invalid agent run");
    }
    conversationId = conversationId == null ? "" : conversationId;
    promptType = Objects.requireNonNull(promptType, "promptType must not be null");
    contextSourceMetrics =
        List.copyOf(
            Objects.requireNonNull(contextSourceMetrics, "contextSourceMetrics must not be null"));
    if (contextSourceMetrics.size() != contextSourceCount)
      throw new IllegalArgumentException("context metrics must match count");
    startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    structuredFailureCategory =
        Objects.requireNonNull(
            structuredFailureCategory, "structuredFailureCategory must not be null");
    if (promptType != PromptType.STRUCTURED
        && (structuredAttemptCount != 0
            || structuredFailureCategory != StructuredFailureCategory.NONE))
      throw new IllegalArgumentException(
          "Only structured runs can contain structured output evidence");
    promptPreview = preview(promptPreview);
    resultPreview = preview(resultPreview);
    errorPreview = preview(errorPreview);
  }

  public AgentRun(
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
    this(
        id,
        conversationId,
        promptType,
        promptPreview,
        selectedToolCount,
        contextSourceCount,
        contextCharacterBudget,
        contextCharactersUsed,
        contextSourceMetrics,
        startedAt,
        completedAt,
        durationMillis,
        status,
        resultPreview,
        errorPreview,
        0,
        StructuredFailureCategory.NONE);
  }

  public Duration duration() {
    return Duration.ofMillis(durationMillis);
  }

  public static String preview(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value);
    return text.length() <= PREVIEW_LIMIT ? text : text.substring(0, PREVIEW_LIMIT);
  }

  public static String errorPreview(Throwable failure) {
    if (failure == null) return null;
    String message = failure.getMessage();
    return preview(
        failure.getClass().getSimpleName()
            + ((message == null || message.isBlank()) ? "" : ": " + message));
  }

  public record ContextSourceMetric(
      String sourceType, int charactersAvailable, int charactersUsed) {
    public ContextSourceMetric {
      if (sourceType == null
          || sourceType.isBlank()
          || charactersAvailable < 0
          || charactersUsed < 0
          || charactersUsed > charactersAvailable)
        throw new IllegalArgumentException("Invalid context source metric");
    }
  }

  public enum PromptType {
    CONVERSATIONAL,
    STRUCTURED
  }

  public enum Status {
    SUCCEEDED,
    FAILED
  }

  public enum StructuredFailureCategory {
    NONE,
    SCHEMA_VALIDATION,
    MODEL_FAILURE
  }
}
