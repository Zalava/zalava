package org.zalava.assistant.chat.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore.Evidence;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

class FileSystemStandingHomeActionEvidenceStoreTest {
  @TempDir Path workspace;

  @Test
  void survivesReopenIsActorScopedAndBounded() throws Exception {
    Actor owner = new Actor(new AccountId(UUID.randomUUID()));
    Actor other = new Actor(new AccountId(UUID.randomUUID()));
    String conversation =
        new ActorConversationId(owner, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    var store = new FileSystemStandingHomeActionEvidenceStore(workspace);
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < FileSystemStandingHomeActionEvidenceStore.MAX_ENTRIES + 3; i++) {
      store.record(
          owner,
          new Evidence(
              start.plusNanos(i).toString(),
              conversation,
              new UUID(0, i + 1).toString(),
              UUID.randomUUID().toString(),
              "light",
              "turn_on",
              "light.room",
              true,
              12));
    }
    var reopened = new FileSystemStandingHomeActionEvidenceStore(workspace);
    assertThat(reopened.recent(owner, conversation))
        .hasSize(FileSystemStandingHomeActionEvidenceStore.MAX_ENTRIES)
        .first()
        .extracting(Evidence::runId)
        .isEqualTo(
            new UUID(0, FileSystemStandingHomeActionEvidenceStore.MAX_ENTRIES + 3).toString());
    assertThat(reopened.recent(owner, conversation))
        .noneMatch(entry -> entry.runId().equals(new UUID(0, 1).toString()))
        .allSatisfy(entry -> assertThat(entry.entityId()).isEqualTo("light.room"));
    assertThatThrownBy(() -> reopened.recent(other, conversation))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reopened.record(other, evidence(conversation)))
        .isInstanceOf(IllegalArgumentException.class);
    try (var files = Files.walk(workspace)) {
      assertThat(
              files
                  .filter(Files::isRegularFile)
                  .filter(path -> path.getFileName().toString().endsWith(".json"))
                  .toList())
          .hasSize(FileSystemStandingHomeActionEvidenceStore.MAX_ENTRIES);
    }
  }

  @Test
  void busyActorStoreDoesNotBlockOrWrite() throws Exception {
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    String conversation =
        new ActorConversationId(actor, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    var store = new FileSystemStandingHomeActionEvidenceStore(workspace);
    assertThat(store.recent(actor, conversation)).isEmpty();
    Path lockPath =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("home-action-evidence")
            .resolve(".lock");
    try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
        FileLock ignored = channel.lock()) {
      assertThatThrownBy(() -> store.record(actor, evidence(conversation)))
          .isInstanceOf(IllegalStateException.class);
    }
    assertThat(store.recent(actor, conversation)).isEmpty();
    store.record(actor, evidence(conversation));
    assertThat(store.recent(actor, conversation)).hasSize(1);
  }

  @Test
  void rejectsMalformedIdentifiersAndIgnoresCorruptFiles() throws Exception {
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    String conversation =
        new ActorConversationId(actor, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    assertThatThrownBy(
            () ->
                new Evidence(
                    Instant.now().toString(),
                    conversation,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "light",
                    "turn_on",
                    "light.*",
                    true,
                    1))
        .isInstanceOf(IllegalArgumentException.class);
    var store = new FileSystemStandingHomeActionEvidenceStore(workspace);
    Evidence retained = evidence(conversation);
    store.record(actor, retained);
    store.record(actor, retained);
    Path directory =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("home-action-evidence");
    Files.writeString(
        directory.resolve("9999999999999999999-999999999-" + UUID.randomUUID() + ".json"),
        "not json");
    assertThat(store.recent(actor, conversation)).hasSize(1);
  }

  private static Evidence evidence(String conversation) {
    return new Evidence(
        Instant.now().toString(),
        conversation,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "light",
        "turn_on",
        "light.room",
        true,
        12);
  }
}
