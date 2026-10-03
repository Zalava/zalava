package org.zalava.capabilities.approval.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Entry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Covers the filesystem approval-request store: atomic save, newest-first loading, deletion, and
 * the UUID allowlist for request identifiers. No approvals are decided in this adapter.
 */
class FileSystemApprovalRequestStoreTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path workspace;

  private static Entry entry(String requestId, String createdAt) {
    JsonNode arguments = JSON.createObjectNode();
    return new Entry(
        requestId,
        createdAt,
        null,
        null,
        null,
        "provider",
        "tool",
        "actor-1",
        Map.of(),
        Map.of("path", "/workspace"),
        List.of("zalava_backed"),
        false,
        arguments,
        "{}",
        null,
        null,
        null);
  }

  @Test
  void savedEntriesRoundTripAndLoadNewestFirst() {
    var store = new FileSystemApprovalRequestStore(workspace);
    Entry older = entry(UUID.randomUUID().toString(), "2026-09-03T10:00:01Z");
    Entry newer = entry(UUID.randomUUID().toString(), "2026-09-03T10:00:02Z");

    store.save(older);
    store.save(newer);

    assertThat(store.load()).containsExactly(newer, older);
    Entry loaded = store.load().getFirst();
    assertThat(loaded.providerId()).isEqualTo("provider");
    assertThat(loaded.toolName()).isEqualTo("tool");
    assertThat(loaded.scope()).containsEntry("path", "/workspace");
    assertThat(loaded.argumentsJson()).isEqualTo("{}");
  }

  @Test
  void deleteRemovesTheEntryAndToleratesAbsence() {
    var store = new FileSystemApprovalRequestStore(workspace);
    Entry saved = entry(UUID.randomUUID().toString(), "2026-09-03T10:00:03Z");
    store.save(saved);

    store.delete(saved.requestId());

    assertThat(store.load()).isEmpty();
    store.delete(saved.requestId());
  }

  @Test
  void nonUuidRequestIdentifiersAreRejected() {
    var store = new FileSystemApprovalRequestStore(workspace);

    assertThatThrownBy(() -> store.save(entry("../../escape", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Zalava approval request id");
    assertThatThrownBy(() -> store.delete("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Zalava approval request id");
  }

  @Test
  void aCorruptStoredDocumentIsRejectedWhenLoaded() throws Exception {
    var store = new FileSystemApprovalRequestStore(workspace);
    Path corrupted =
        Files.createDirectories(workspace.resolve("approval-requests"))
            .resolve(UUID.randomUUID() + ".json");
    Files.writeString(corrupted, "not-json");

    assertThatThrownBy(store::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to read Zalava tool approval request");
  }
}
