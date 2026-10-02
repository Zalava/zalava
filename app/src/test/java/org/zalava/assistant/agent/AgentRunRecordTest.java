package org.zalava.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunRecordTest {

  @Test
  void validRecord() {
    var record = validBuilder().build();
    assertThat(record.id()).isEqualTo("run-1");
    assertThat(record.conversationId()).isEqualTo("conv-1");
    assertThat(record.promptType()).isEqualTo(AgentRunRecord.PromptType.CONVERSATIONAL);
    assertThat(record.promptPreview()).isEqualTo("hello");
    assertThat(record.selectedToolCount()).isEqualTo(2);
    assertThat(record.contextSourceCount()).isEqualTo(1);
    assertThat(record.contextCharacterBudget()).isEqualTo(1000);
    assertThat(record.contextCharactersUsed()).isEqualTo(500);
    assertThat(record.status()).isEqualTo(AgentRunRecord.Status.SUCCEEDED);
    assertThat(record.duration()).isEqualTo(java.time.Duration.ofMillis(100));
  }

  @Test
  void nullConversationIdDefaultsToEmpty() {
    var record = validBuilder().conversationId(null).build();
    assertThat(record.conversationId()).isEmpty();
  }

  @Test
  void longPromptPreviewIsTruncated() {
    String longPrompt = "x".repeat(600);
    var record = validBuilder().promptPreview(longPrompt).build();
    assertThat(record.promptPreview()).hasSize(AgentRunRecord.PREVIEW_LIMIT);
  }

  @Test
  void nullPromptPreviewStaysNull() {
    var record = validBuilder().promptPreview(null).build();
    assertThat(record.promptPreview()).isNull();
  }

  @Test
  void rejectsBlankId() {
    assertThatThrownBy(() -> validBuilder().id("").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be blank");
  }

  @Test
  void rejectsNullId() {
    assertThatThrownBy(() -> validBuilder().id(null).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullPromptType() {
    assertThatThrownBy(
            () ->
                new AgentRunRecord(
                    "run-1",
                    "conv",
                    null,
                    "p",
                    0,
                    0,
                    100,
                    0,
                    List.of(),
                    Instant.now(),
                    Instant.now(),
                    0,
                    AgentRunRecord.Status.SUCCEEDED,
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNegativeSelectedToolCount() {
    assertThatThrownBy(() -> validBuilder().selectedToolCount(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("selectedToolCount must not be negative");
  }

  @Test
  void rejectsNegativeContextSourceCount() {
    assertThatThrownBy(
            () -> validBuilder().contextSourceCount(-1).contextSourceMetrics(List.of()).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contextSourceCount must not be negative");
  }

  @Test
  void rejectsNegativeContextCharacterBudget() {
    assertThatThrownBy(() -> validBuilder().contextCharacterBudget(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contextCharacterBudget must not be negative");
  }

  @Test
  void rejectsNegativeContextCharactersUsed() {
    assertThatThrownBy(() -> validBuilder().contextCharactersUsed(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contextCharactersUsed must not be negative");
  }

  @Test
  void rejectsContextCharactersUsedExceedingBudget() {
    assertThatThrownBy(
            () -> validBuilder().contextCharacterBudget(100).contextCharactersUsed(200).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed contextCharacterBudget");
  }

  @Test
  void rejectsNullContextSourceMetrics() {
    assertThatThrownBy(() -> validBuilder().contextSourceMetrics(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsContextSourceMetricsSizeMismatch() {
    var metrics = List.of(new AgentRunRecord.ContextSourceMetric("file", 100, 50));
    assertThatThrownBy(
            () -> validBuilder().contextSourceCount(2).contextSourceMetrics(metrics).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match contextSourceCount");
  }

  @Test
  void rejectsNullStartedAt() {
    assertThatThrownBy(() -> validBuilder().startedAt(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullCompletedAt() {
    assertThatThrownBy(() -> validBuilder().completedAt(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNegativeDurationMillis() {
    assertThatThrownBy(() -> validBuilder().durationMillis(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("durationMillis must not be negative");
  }

  @Test
  void rejectsNullStatus() {
    assertThatThrownBy(
            () ->
                new AgentRunRecord(
                    "run-1",
                    "conv",
                    AgentRunRecord.PromptType.CONVERSATIONAL,
                    "p",
                    0,
                    0,
                    100,
                    0,
                    List.of(),
                    Instant.now(),
                    Instant.now(),
                    0,
                    null,
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void longResultPreviewIsTruncated() {
    String longResult = "y".repeat(600);
    var record = validBuilder().resultPreview(longResult).build();
    assertThat(record.resultPreview()).hasSize(AgentRunRecord.PREVIEW_LIMIT);
  }

  @Test
  void nullResultPreviewStaysNull() {
    var record = validBuilder().resultPreview(null).build();
    assertThat(record.resultPreview()).isNull();
  }

  @Test
  void longErrorPreviewIsTruncated() {
    String longError = "z".repeat(600);
    var record = validBuilder().errorPreview(longError).build();
    assertThat(record.errorPreview()).hasSize(AgentRunRecord.PREVIEW_LIMIT);
  }

  @Test
  void nullErrorPreviewStaysNull() {
    var record = validBuilder().errorPreview(null).build();
    assertThat(record.errorPreview()).isNull();
  }

  @Test
  void contextSourceMetricValidatesBlankSourceType() {
    assertThatThrownBy(() -> new AgentRunRecord.ContextSourceMetric("", 100, 50))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceType must not be blank");
  }

  @Test
  void contextSourceMetricValidatesNegativeAvailable() {
    assertThatThrownBy(() -> new AgentRunRecord.ContextSourceMetric("file", -1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("charactersAvailable must not be negative");
  }

  @Test
  void contextSourceMetricValidatesNegativeUsed() {
    assertThatThrownBy(() -> new AgentRunRecord.ContextSourceMetric("file", 100, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("charactersUsed must not be negative");
  }

  @Test
  void contextSourceMetricValidatesUsedExceedsAvailable() {
    assertThatThrownBy(() -> new AgentRunRecord.ContextSourceMetric("file", 50, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed charactersAvailable");
  }

  @Test
  void errorPreviewWithNullMessage() {
    String result = AgentRunRecord.errorPreview(new RuntimeException());
    assertThat(result).startsWith("RuntimeException");
  }

  @Test
  void errorPreviewWithBlankMessage() {
    String result = AgentRunRecord.errorPreview(new RuntimeException("  "));
    assertThat(result).startsWith("RuntimeException");
  }

  @Test
  void errorPreviewWithNullThrowable() {
    assertThat(AgentRunRecord.errorPreview(null)).isNull();
  }

  @Test
  void previewWithNull() {
    assertThat(AgentRunRecord.preview(null)).isNull();
  }

  @Test
  void promptTypeValues() {
    assertThat(AgentRunRecord.PromptType.values())
        .containsExactlyInAnyOrder(
            AgentRunRecord.PromptType.CONVERSATIONAL, AgentRunRecord.PromptType.STRUCTURED);
  }

  @Test
  void statusValues() {
    assertThat(AgentRunRecord.Status.values())
        .containsExactlyInAnyOrder(AgentRunRecord.Status.SUCCEEDED, AgentRunRecord.Status.FAILED);
  }

  private static AgentRunRecordBuilder validBuilder() {
    return new AgentRunRecordBuilder();
  }

  private static class AgentRunRecordBuilder {
    private String id = "run-1";
    private String conversationId = "conv-1";
    private AgentRunRecord.PromptType promptType = AgentRunRecord.PromptType.CONVERSATIONAL;
    private String promptPreview = "hello";
    private int selectedToolCount = 2;
    private int contextSourceCount = 1;
    private int contextCharacterBudget = 1000;
    private int contextCharactersUsed = 500;
    private List<AgentRunRecord.ContextSourceMetric> contextSourceMetrics =
        List.of(new AgentRunRecord.ContextSourceMetric("file", 1000, 500));
    private Instant startedAt = Instant.parse("2025-01-01T00:00:00Z");
    private Instant completedAt = Instant.parse("2025-01-01T00:00:00.100Z");
    private long durationMillis = 100;
    private AgentRunRecord.Status status = AgentRunRecord.Status.SUCCEEDED;
    private String resultPreview = "done";
    private String errorPreview = null;

    AgentRunRecordBuilder id(String v) {
      id = v;
      return this;
    }

    AgentRunRecordBuilder conversationId(String v) {
      conversationId = v;
      return this;
    }

    AgentRunRecordBuilder promptType(AgentRunRecord.PromptType v) {
      promptType = v;
      return this;
    }

    AgentRunRecordBuilder promptPreview(String v) {
      promptPreview = v;
      return this;
    }

    AgentRunRecordBuilder selectedToolCount(int v) {
      selectedToolCount = v;
      return this;
    }

    AgentRunRecordBuilder contextSourceCount(int v) {
      contextSourceCount = v;
      return this;
    }

    AgentRunRecordBuilder contextCharacterBudget(int v) {
      contextCharacterBudget = v;
      return this;
    }

    AgentRunRecordBuilder contextCharactersUsed(int v) {
      contextCharactersUsed = v;
      return this;
    }

    AgentRunRecordBuilder contextSourceMetrics(List<AgentRunRecord.ContextSourceMetric> v) {
      contextSourceMetrics = v;
      return this;
    }

    AgentRunRecordBuilder startedAt(Instant v) {
      startedAt = v;
      return this;
    }

    AgentRunRecordBuilder completedAt(Instant v) {
      completedAt = v;
      return this;
    }

    AgentRunRecordBuilder durationMillis(long v) {
      durationMillis = v;
      return this;
    }

    AgentRunRecordBuilder status(AgentRunRecord.Status v) {
      status = v;
      return this;
    }

    AgentRunRecordBuilder resultPreview(String v) {
      resultPreview = v;
      return this;
    }

    AgentRunRecordBuilder errorPreview(String v) {
      errorPreview = v;
      return this;
    }

    AgentRunRecord build() {
      return new AgentRunRecord(
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
          errorPreview);
    }
  }
}
