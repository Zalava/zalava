package org.zalava.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class FileSystemAgentMemoryStoreTest {

  @TempDir Path workspaceDir;

  @Test
  void persistsAndReloadsMemories() throws IOException {
    FileSystemAgentMemoryStore store =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));

    AgentMemory saved =
        store.remember(
            new AgentMemoryDraft(
                AgentMemoryScope.PROJECT,
                "SEA uses a provider-instance runtime.",
                Map.of("source", "test")));

    FileSystemAgentMemoryStore reloaded =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));
    assertThat(reloaded.recent(10))
        .singleElement()
        .satisfies(
            memory -> {
              assertThat(memory.id()).isEqualTo(saved.id());
              assertThat(memory.scope()).isEqualTo(AgentMemoryScope.PROJECT);
              assertThat(memory.text()).isEqualTo("SEA uses a provider-instance runtime.");
              assertThat(memory.metadata()).containsEntry("source", "test");
            });
    assertThat(workspaceDir.resolve("memory")).isDirectory();
  }

  @Test
  void returnsRecentMemoriesNewestFirstWithLimit() throws IOException, InterruptedException {
    FileSystemAgentMemoryStore store =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));

    AgentMemory first =
        store.remember(new AgentMemoryDraft(AgentMemoryScope.USER, "first", Map.of()));
    Thread.sleep(2);
    AgentMemory second =
        store.remember(new AgentMemoryDraft(AgentMemoryScope.USER, "second", Map.of()));
    Thread.sleep(2);
    AgentMemory third =
        store.remember(new AgentMemoryDraft(AgentMemoryScope.USER, "third", Map.of()));

    assertThat(store.recent(2))
        .extracting(AgentMemory::id)
        .containsExactly(third.id(), second.id());
    assertThat(store.recent(10))
        .extracting(AgentMemory::id)
        .containsExactly(third.id(), second.id(), first.id());
  }

  @Test
  void searchesTextScopeAndMetadata() throws IOException {
    FileSystemAgentMemoryStore store =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));
    AgentMemory project =
        store.remember(
            new AgentMemoryDraft(
                AgentMemoryScope.PROJECT,
                "Use draft pull requests for SEA steps.",
                Map.of("topic", "workflow")));
    AgentMemory execution =
        store.remember(
            new AgentMemoryDraft(
                AgentMemoryScope.EXECUTION,
                "Gradle cache lives under .gradle/sea-workflow.",
                Map.of("tool", "gradle")));

    assertThat(store.search("draft pull", 10))
        .extracting(AgentMemory::id)
        .containsExactly(project.id());
    assertThat(store.search("execution", 10))
        .extracting(AgentMemory::id)
        .containsExactly(execution.id());
    assertThat(store.search("gradle", 10))
        .extracting(AgentMemory::id)
        .containsExactly(execution.id());
  }

  @Test
  void skipsInvalidPersistedFiles() throws IOException {
    FileSystemAgentMemoryStore store =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));
    AgentMemory valid =
        store.remember(new AgentMemoryDraft(AgentMemoryScope.AGENT, "valid memory", Map.of()));
    Path invalid = Files.createDirectories(workspaceDir.resolve("memory")).resolve("invalid.yaml");
    Files.writeString(invalid, "---\nid: invalid\nscope: missing-fields\n");

    assertThat(store.recent(10)).extracting(AgentMemory::id).containsExactly(valid.id());
  }

  @Test
  void validatesDraftsAndLimits() throws IOException {
    FileSystemAgentMemoryStore store =
        new FileSystemAgentMemoryStore(new FileSystemResource(workspaceDir));

    assertThatThrownBy(() -> new AgentMemoryDraft(AgentMemoryScope.USER, " ", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("text must not be blank");
    assertThatThrownBy(() -> store.recent(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
    assertThatThrownBy(() -> store.search("query", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
  }
}
