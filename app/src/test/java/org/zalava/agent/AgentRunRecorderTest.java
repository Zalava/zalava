package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.agent.domain.AgentRun;

class AgentRunRecorderTest {

  private final InMemoryAgentRunRecorder recorder = new InMemoryAgentRunRecorder();

  @Test
  void storesBoundedRunRecordPreviews() {
    recorder.record(
        new AgentRun(
            "run-1",
            "conversation-1",
            AgentRun.PromptType.CONVERSATIONAL,
            "p".repeat(700),
            3,
            1,
            1_000,
            500,
            List.of(new AgentRun.ContextSourceMetric("user_prompt", 700, 500)),
            Instant.parse("2026-06-20T10:00:00Z"),
            Instant.parse("2026-06-20T10:00:02Z"),
            2_000,
            AgentRun.Status.SUCCEEDED,
            "r".repeat(700),
            null));

    AgentRun record = recorder.recent().getFirst();

    assertThat(record.promptPreview()).hasSize(500);
    assertThat(record.resultPreview()).hasSize(500);
    assertThat(record.contextSourceMetrics())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.sourceType()).isEqualTo("user_prompt");
              assertThat(source.charactersAvailable()).isEqualTo(700);
              assertThat(source.charactersUsed()).isEqualTo(500);
            });
    assertThat(record.duration()).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void returnsDefensiveRecentSnapshot() {
    recorder.record(record("run-1"));
    List<AgentRun> snapshot = recorder.recent();

    recorder.record(record("run-2"));

    assertThat(snapshot).extracting(AgentRun::id).containsExactly("run-1");
    assertThatThrownBy(() -> snapshot.add(record("run-3")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void limitsRecentRecordsToMostRecentEntries() {
    InMemoryAgentRunRecorder boundedRecorder = new InMemoryAgentRunRecorder(2);

    boundedRecorder.record(record("run-1"));
    boundedRecorder.record(record("run-2"));
    boundedRecorder.record(record("run-3"));

    assertThat(boundedRecorder.recent()).extracting(AgentRun::id).containsExactly("run-2", "run-3");
  }

  private static AgentRun record(String id) {
    Instant startedAt = Instant.parse("2026-06-20T10:00:00Z");
    Instant completedAt = Instant.parse("2026-06-20T10:00:01Z");
    return new AgentRun(
        id,
        "conversation-1",
        AgentRun.PromptType.STRUCTURED,
        "prompt",
        1,
        1,
        1_000,
        6,
        List.of(new AgentRun.ContextSourceMetric("user_prompt", 6, 6)),
        startedAt,
        completedAt,
        1_000,
        AgentRun.Status.SUCCEEDED,
        "result",
        null);
  }
}
