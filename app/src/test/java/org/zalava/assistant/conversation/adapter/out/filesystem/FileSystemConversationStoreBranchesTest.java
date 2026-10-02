package org.zalava.assistant.conversation.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.conversation.domain.ConversationMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

/**
 * Covers the remaining filesystem conversation persistence branches: append semantics, frontmatter
 * preservation, deletion, filtering of non-conversation files, and the actor-scoped variants. All
 * writes stay inside a temporary workspace.
 */
class FileSystemConversationStoreBranchesTest {

  @TempDir Path workspace;

  @Test
  void missingConversationsYieldEmptyResultsInsteadOfErrors() {
    var store = new FileSystemConversationStore(workspace);

    assertThat(store.findConversationIds()).isEmpty();
    assertThat(store.findByConversationId("absent")).isEmpty();
  }

  @Test
  void findConversationIdsIgnoresFilesThatAreNotChatYamlDocuments() throws Exception {
    Files.createDirectories(workspace.resolve("conversations"));
    Files.writeString(workspace.resolve("conversations/chat-web.yaml"), "---\ncreatedAt: t\n");
    Files.writeString(workspace.resolve("conversations/notes.yaml"), "---\n");
    Files.writeString(workspace.resolve("conversations/chat-partial.txt"), "nope");

    assertThat(new FileSystemConversationStore(workspace).findConversationIds())
        .containsExactly("web");
  }

  @Test
  void appendAllKeepsEarlierMessages() {
    var store = new FileSystemConversationStore(workspace);
    store.saveAll("web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "first")));

    store.appendAll(
        "web", List.of(new ConversationMessage(ConversationMessage.Role.ASSISTANT, "second")));

    assertThat(store.findByConversationId("web"))
        .extracting(ConversationMessage::text)
        .containsExactly("first", "second");
  }

  @Test
  void saveAllPreservesTheOriginalCreatedAtFrontmatter() throws Exception {
    var store = new FileSystemConversationStore(workspace);
    store.saveAll("web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "hello")));
    String original = Files.readString(workspace.resolve("conversations/chat-web.yaml"));
    String originalCreatedAt = frontmatterValue(original, "createdAt");

    Thread.sleep(5);
    store.saveAll(
        "web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "replacement")));

    String updated = Files.readString(workspace.resolve("conversations/chat-web.yaml"));
    assertThat(frontmatterValue(updated, "createdAt")).isEqualTo(originalCreatedAt);
    assertThat(frontmatterValue(updated, "updatedAt")).isNotEqualTo(originalCreatedAt);
    assertThat(updated).doesNotContain("hello");
  }

  @Test
  void deleteByConversationIdRemovesTheFileAndToleratesAbsence() {
    var store = new FileSystemConversationStore(workspace);
    store.saveAll("web", List.of(new ConversationMessage(ConversationMessage.Role.USER, "hi")));

    store.deleteByConversationId("web");

    assertThat(store.findByConversationId("web")).isEmpty();
    store.deleteByConversationId("web");
  }

  @Test
  void actorScopedAppendAndDeleteRoundTrip() {
    var store = new FileSystemConversationStore(workspace);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();

    store.saveAll(
        actor, reference, List.of(new ConversationMessage(ConversationMessage.Role.USER, "one")));
    store.appendAll(
        actor,
        reference,
        List.of(new ConversationMessage(ConversationMessage.Role.ASSISTANT, "two")));

    assertThat(store.findByReference(actor, reference))
        .extracting(ConversationMessage::text)
        .containsExactly("one", "two");

    store.delete(actor, reference);
    assertThat(store.findByReference(actor, reference)).isEmpty();
    store.delete(actor, reference);
  }

  @Test
  void actorScopedSaveAllPreservesTheOriginalCreatedAt() throws Exception {
    var store = new FileSystemConversationStore(workspace);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();
    store.saveAll(
        actor, reference, List.of(new ConversationMessage(ConversationMessage.Role.USER, "one")));
    Path file =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("conversations")
            .resolve(reference.value() + ".yaml");
    String originalCreatedAt = frontmatterValue(Files.readString(file), "createdAt");

    store.saveAll(
        actor, reference, List.of(new ConversationMessage(ConversationMessage.Role.USER, "two")));

    assertThat(frontmatterValue(Files.readString(file), "createdAt")).isEqualTo(originalCreatedAt);
  }

  @Test
  void findReferencesOnlyListsYamlFiles() throws Exception {
    var store = new FileSystemConversationStore(workspace);
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();
    store.saveAll(
        actor, reference, List.of(new ConversationMessage(ConversationMessage.Role.USER, "one")));
    Path directory =
        workspace.resolve("users").resolve(actor.accountId().toString()).resolve("conversations");
    Files.writeString(directory.resolve("notes.txt"), "ignored");

    assertThat(store.findReferences(actor)).containsExactly(reference);
  }

  @Test
  void messagesRejectBlankText() {
    assertThatThrownBy(() -> new ConversationMessage(ConversationMessage.Role.USER, "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConversationMessage(null, "text"))
        .isInstanceOf(NullPointerException.class);
  }

  private static String frontmatterValue(String yaml, String key) {
    for (String line : yaml.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith(key + ":")) {
        return trimmed.substring(key.length() + 1).trim();
      }
    }
    throw new AssertionError("frontmatter key not found: " + key + " in " + yaml);
  }
}
