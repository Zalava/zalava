package org.zalava.assistant.conversation.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.conversation.domain.ConversationMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

class FileSystemConversationStoreTest {
  @TempDir Path workspace;

  @Test
  void persistsExistingConversationYamlRolesAndText() {
    var store = new FileSystemConversationStore(workspace);
    store.saveAll(
        "web",
        List.of(
            new ConversationMessage(ConversationMessage.Role.USER, "hello"),
            new ConversationMessage(ConversationMessage.Role.ASSISTANT, "hi")));
    assertThat(new FileSystemConversationStore(workspace).findByConversationId("web"))
        .extracting(ConversationMessage::text)
        .containsExactly("hello", "hi");
    assertThat(store.findConversationIds()).contains("web");
  }

  @Test
  void actorScopedConversationsAreOpaqueAndIsolated() {
    var store = new FileSystemConversationStore(workspace);
    Actor first = new Actor(AccountId.newId());
    Actor second = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();

    store.saveAll(
        first,
        reference,
        List.of(new ConversationMessage(ConversationMessage.Role.USER, "private first")));

    assertThat(store.findReferences(first)).containsExactly(reference);
    assertThat(store.findByReference(first, reference))
        .containsExactly(new ConversationMessage(ConversationMessage.Role.USER, "private first"));
    assertThat(store.findReferences(second)).isEmpty();
    assertThat(store.findByReference(second, reference)).isEmpty();
    assertThat(reference.value()).doesNotContain("/", "\\", "conversations");
  }
}
