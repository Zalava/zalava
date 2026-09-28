package org.zalava.clarification.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.zalava.clarification.domain.ClarificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the filesystem clarification store: atomic save, newest-first loading, deletion and the
 * UUID allowlist for request identifiers. No clarification is answered in this adapter.
 */
class FileSystemClarificationRequestStoreTest {

  @TempDir Path workspace;

  @Test
  void savedRequestsRoundTripAndLoadNewestFirst() {
    var store = new FileSystemClarificationRequestStore(workspace);
    ClarificationRequest older = request(UUID.randomUUID().toString(), "2026-09-16T10:00:01Z");
    ClarificationRequest newer = request(UUID.randomUUID().toString(), "2026-09-16T10:00:02Z");

    store.save(older);
    store.save(newer);

    assertThat(store.load()).containsExactly(newer, older);
    ClarificationRequest loaded = store.load().getFirst();
    assertThat(loaded.actorId()).isEqualTo("actor-1");
    assertThat(loaded.taskReference()).isEqualTo("task-1");
    assertThat(loaded.questions()).hasSize(1);
    assertThat(loaded.questions().getFirst().choices())
        .extracting(ClarificationRequest.Choice::choiceId)
        .containsExactly("a", "b");
  }

  @Test
  void deleteRemovesTheRequestAndToleratesAbsence() {
    var store = new FileSystemClarificationRequestStore(workspace);
    ClarificationRequest saved = request(UUID.randomUUID().toString(), "2026-09-16T10:00:03Z");
    store.save(saved);

    store.delete(saved.requestId());

    assertThat(store.load()).isEmpty();
    store.delete(saved.requestId());
  }

  @Test
  void nonUuidRequestIdentifiersAreRejected() {
    var store = new FileSystemClarificationRequestStore(workspace);

    assertThatThrownBy(() -> store.save(request("../../escape", "2026-09-16T10:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid SEA clarification request id");
    assertThatThrownBy(() -> store.delete("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid SEA clarification request id");
  }

  @Test
  void aCorruptStoredDocumentIsRejectedWhenLoaded() throws Exception {
    var store = new FileSystemClarificationRequestStore(workspace);
    Path corrupted =
        Files.createDirectories(workspace.resolve("clarification-requests"))
            .resolve(UUID.randomUUID() + ".json");
    Files.writeString(corrupted, "not-json");

    assertThatThrownBy(store::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to read SEA clarification request");
  }

  static ClarificationRequest request(String requestId, String createdAt) {
    return new ClarificationRequest(
        requestId,
        "actor-1",
        "task-1",
        "Which report?",
        List.of(
            new ClarificationRequest.Question(
                "q1",
                "Which report?",
                List.of(
                    new ClarificationRequest.Choice("a", "Monthly"),
                    new ClarificationRequest.Choice("b", "Weekly")),
                false)),
        Map.of(),
        ClarificationRequest.Status.PENDING,
        createdAt,
        "2026-09-17T10:00:00Z",
        null,
        null,
        List.of());
  }
}
