package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.agent.domain.AgentRun;

/**
 * Covers the remaining guard and eviction behavior of the in-memory run store that the shared
 * {@link AgentRunRecorderTest} does not exercise.
 */
class InMemoryAgentRunRecorderLimitsTest {

  @Test
  void rejectsNonPositiveLimits() {
    assertThatThrownBy(() -> new InMemoryAgentRunRecorder(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be positive");
    assertThatThrownBy(() -> new InMemoryAgentRunRecorder(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be positive");
  }

  @Test
  void defaultConstructorKeepsMostRecentRecords() {
    InMemoryAgentRunRecorder recorder = new InMemoryAgentRunRecorder();

    for (int i = 1; i <= 201; i++) {
      recorder.record(record("run-" + i));
    }

    List<AgentRun> recent = recorder.recent();
    assertThat(recent).hasSize(200);
    assertThat(recent.getFirst().id()).isEqualTo("run-2");
    assertThat(recent.getLast().id()).isEqualTo("run-201");
  }

  private static AgentRun record(String id) {
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
        java.time.Instant.parse("2026-06-20T10:00:00Z"),
        java.time.Instant.parse("2026-06-20T10:00:01Z"),
        1_000,
        AgentRun.Status.SUCCEEDED,
        "result",
        null);
  }
}
