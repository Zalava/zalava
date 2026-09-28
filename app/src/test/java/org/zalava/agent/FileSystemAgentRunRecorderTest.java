package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.adapter.out.filesystem.FileSystemAgentRunRecorder;
import org.zalava.agent.domain.AgentRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class FileSystemAgentRunRecorderTest {

  @TempDir Path workspaceDir;

  @Test
  void persistsAndReloadsRecordsAcrossInstances() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    AgentRun record = record("run-1", "conversation-1", Instant.parse("2026-06-20T10:00:00Z"));

    recorder.record(record);

    FileSystemAgentRunRecorder reloaded =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    assertThat(reloaded.recent()).singleElement().isEqualTo(record);
    assertThat(workspaceDir.resolve("agent-runs")).isDirectory();
  }

  @Test
  void returnsEmptyListWhenDirectoryDoesNotExist() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));

    assertThat(recorder.recent()).isEmpty();
    assertThat(workspaceDir.resolve("agent-runs")).doesNotExist();
  }

  @Test
  void returnsRecordsSortedByStartedAt() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    AgentRun later = record("run-2", "conversation-2", Instant.parse("2026-06-20T10:00:02Z"));
    AgentRun earlier = record("run-1", "conversation-1", Instant.parse("2026-06-20T10:00:00Z"));

    recorder.record(later);
    recorder.record(earlier);

    assertThat(recorder.recent()).extracting(AgentRun::id).containsExactly("run-1", "run-2");
  }

  @Test
  void preservesBoundedPreviews() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    AgentRun record =
        new AgentRun(
            "run-long",
            "conversation-1",
            AgentRun.PromptType.CONVERSATIONAL,
            "p".repeat(700),
            1,
            1,
            1_000,
            500,
            List.of(new AgentRun.ContextSourceMetric("user_prompt", 700, 500)),
            Instant.parse("2026-06-20T10:00:00Z"),
            Instant.parse("2026-06-20T10:00:01Z"),
            1_000,
            AgentRun.Status.SUCCEEDED,
            "r".repeat(700),
            "e".repeat(700));

    recorder.record(record);

    assertThat(recorder.recent())
        .singleElement()
        .satisfies(
            reloaded -> {
              assertThat(reloaded.promptPreview()).hasSize(AgentRun.PREVIEW_LIMIT);
              assertThat(reloaded.resultPreview()).hasSize(AgentRun.PREVIEW_LIMIT);
              assertThat(reloaded.errorPreview()).hasSize(AgentRun.PREVIEW_LIMIT);
            });
  }

  @Test
  void preservesPreviewTextThatLooksLikeFrontmatter() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    AgentRun record =
        new AgentRun(
            "run-frontmatter-text",
            "conversation-1",
            AgentRun.PromptType.CONVERSATIONAL,
            "first line\nstatus: should stay in preview",
            1,
            1,
            1_000,
            42,
            List.of(new AgentRun.ContextSourceMetric("user_prompt", 42, 42)),
            Instant.parse("2026-06-20T10:00:00Z"),
            Instant.parse("2026-06-20T10:00:01Z"),
            1_000,
            AgentRun.Status.SUCCEEDED,
            "result line\nid: not a record id",
            "error line\nstartedAt: not an instant");

    recorder.record(record);

    assertThat(recorder.recent())
        .singleElement()
        .satisfies(
            reloaded -> {
              assertThat(reloaded.promptPreview()).isEqualTo(record.promptPreview());
              assertThat(reloaded.resultPreview()).isEqualTo(record.resultPreview());
              assertThat(reloaded.errorPreview()).isEqualTo(record.errorPreview());
              assertThat(reloaded.contextSourceMetrics()).isEqualTo(record.contextSourceMetrics());
            });
  }

  @Test
  void skipsInvalidPersistedFiles() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    recorder.record(record("run-1", "conversation-1", Instant.parse("2026-06-20T10:00:00Z")));
    Path invalid =
        Files.createDirectories(workspaceDir.resolve("agent-runs")).resolve("invalid.yaml");
    Files.writeString(invalid, "---\nid: invalid\nstartedAt: not-an-instant\n");

    assertThat(recorder.recent()).extracting(AgentRun::id).containsExactly("run-1");
  }

  @Test
  void actorScopedRunRecordsCannotCrossOwnerBoundaries() throws IOException {
    FileSystemAgentRunRecorder recorder =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspaceDir));
    Actor first = new Actor(AccountId.newId());
    Actor second = new Actor(AccountId.newId());
    AgentRun run = record("run-1", "conversation-1", Instant.parse("2026-06-20T10:00:00Z"));

    recorder.record(first, run);

    assertThat(recorder.recent(first)).containsExactly(run);
    assertThat(recorder.recent(second)).isEmpty();
  }

  private static AgentRun record(String id, String conversationId, Instant startedAt) {
    return new AgentRun(
        id,
        conversationId,
        AgentRun.PromptType.STRUCTURED,
        "prompt-" + id,
        2,
        1,
        1_000,
        12,
        List.of(new AgentRun.ContextSourceMetric("user_prompt", 12, 12)),
        startedAt,
        startedAt.plusSeconds(1),
        1_000,
        AgentRun.Status.SUCCEEDED,
        "result-" + id,
        null);
  }
}
