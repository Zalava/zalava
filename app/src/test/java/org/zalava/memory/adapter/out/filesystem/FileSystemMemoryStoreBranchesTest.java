package org.zalava.memory.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.files.YamlParser;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryScope;

/**
 * Covers the remaining filesystem memory-store branches: scope/metadata search matching, recent
 * ordering with tie-breakers, limit validation, corrupt-file tolerance, and blank-query handling.
 */
class FileSystemMemoryStoreBranchesTest {

  @TempDir Path workspace;

  @Test
  void searchMatchesTextScopeAndMetadataValues() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(
        new MemoryDraft(MemoryScope.PROJECT, "graphite pencil notes", Map.of("topic", "art")));
    store.remember(
        new MemoryDraft(MemoryScope.USER, "prefers concise answers", Map.of("channel", "chat")));

    assertThat(store.search("graphite", 10)).hasSize(1);
    assertThat(store.search("user", 10)).hasSize(1);
    assertThat(store.search("art", 10)).hasSize(1);
    assertThat(store.search("absent-topic", 10)).isEmpty();
  }

  @Test
  void blankQueriesSkipTheSearchAndReturnNothing() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(new MemoryDraft(MemoryScope.PROJECT, "remembered text", Map.of()));

    assertThat(store.search("", 10)).isEmpty();
    assertThat(store.search("   ", 10)).isEmpty();
    assertThat(store.search(null, 10)).isEmpty();
  }

  @Test
  void recentReturnsNewestFirstAndHonorsTheLimit() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(new MemoryDraft(MemoryScope.PROJECT, "oldest", Map.of()));
    store.remember(new MemoryDraft(MemoryScope.PROJECT, "middle", Map.of()));
    store.remember(new MemoryDraft(MemoryScope.PROJECT, "newest", Map.of()));

    List<Memory> recents = store.recent(2);

    assertThat(recents).hasSize(2);
    assertThat(recents.get(0).text()).isEqualTo("newest");
  }

  @Test
  void limitsMustBePositive() {
    var store = new FileSystemMemoryStore(workspace);

    assertThatThrownBy(() -> store.recent(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
    assertThatThrownBy(() -> store.search("x", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
    assertThatThrownBy(() -> store.recent(null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
  }

  @Test
  void corruptMemoryFilesAreSkippedInsteadOfFailingTheListing() throws Exception {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(new MemoryDraft(MemoryScope.PROJECT, "valid memory", Map.of()));
    Files.createDirectories(workspace.resolve("memory"));
    Files.writeString(workspace.resolve("memory/broken.yaml"), "not: [valid");
    Files.writeString(
        workspace.resolve("memory/missing-fields.yaml"),
        YamlParser.serialize(new org.zalava.files.YamlDocument(Map.of("id", "only-id"), null)));

    assertThat(store.recent(10))
        .singleElement()
        .satisfies(memory -> assertThat(memory.text()).isEqualTo("valid memory"));
  }

  @Test
  void metadataValuesRoundTripThroughBase64Frontmatter() {
    var store = new FileSystemMemoryStore(workspace);

    store.remember(
        new MemoryDraft(MemoryScope.PROJECT, "with metadata", Map.of("topic", "multi word value")));

    assertThat(store.search("multi word", 10))
        .singleElement()
        .satisfies(
            memory -> assertThat(memory.metadata()).containsEntry("topic", "multi word value"));
  }

  @Test
  void actorScopedSearchAndRecentStayIsolatedAndValidateLimits() {
    var store = new FileSystemMemoryStore(workspace);
    Actor actor = new Actor(AccountId.newId());
    store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "private context", Map.of()));

    assertThat(store.recent(actor, 10)).hasSize(1);
    assertThat(store.search(actor, "private", 10)).hasSize(1);
    assertThat(store.search(actor, "", 10)).isEmpty();
    assertThat(new FileSystemMemoryStore(workspace).recent(10)).isEmpty();
  }

  @Test
  void rememberRejectsDraftsMissingRequiredFields() {
    var store = new FileSystemMemoryStore(workspace);

    assertThatThrownBy(() -> store.remember(new MemoryDraft(MemoryScope.PROJECT, "  ", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> store.remember(null, new MemoryDraft(MemoryScope.PROJECT, "t", Map.of())))
        .isInstanceOf(NullPointerException.class);
  }
}
