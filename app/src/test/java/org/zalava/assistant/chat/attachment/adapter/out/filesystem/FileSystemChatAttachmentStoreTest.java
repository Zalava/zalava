package org.zalava.assistant.chat.attachment.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.chat.attachment.domain.ChatAttachment;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

class FileSystemChatAttachmentStoreTest {

  @TempDir Path workspace;

  @Test
  void roundTripsAttachmentsWithinTheOwnerScope() {
    FileSystemChatAttachmentStore store = new FileSystemChatAttachmentStore(workspace);
    Actor owner = new Actor(AccountId.newId());
    ChatAttachment attachment = ChatAttachment.create(owner, "notes.txt", "text/plain", 5, "abc");
    byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

    store.save(owner, attachment, content);

    assertThat(store.find(owner, attachment.id())).contains(attachment);
    assertThat(store.list(owner)).containsExactly(attachment);
    assertThat(store.read(owner, attachment.id())).contains(content);

    store.delete(owner, attachment.id());
    assertThat(store.find(owner, attachment.id())).isEmpty();
    assertThat(store.read(owner, attachment.id())).isEmpty();
    assertThat(store.list(owner)).isEmpty();
  }

  @Test
  void missingMetadataAndBlobReadAsEmpty() {
    FileSystemChatAttachmentStore store = new FileSystemChatAttachmentStore(workspace);
    Actor owner = new Actor(AccountId.newId());

    assertThat(store.find(owner, "00000000-0000-0000-0000-000000000000")).isEmpty();
    assertThat(store.read(owner, "00000000-0000-0000-0000-000000000000")).isEmpty();
  }

  @Test
  void isolatesContentBetweenActors() {
    FileSystemChatAttachmentStore store = new FileSystemChatAttachmentStore(workspace);
    Actor owner = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    ChatAttachment attachment = ChatAttachment.create(owner, "notes.txt", "text/plain", 5, "abc");
    store.save(owner, attachment, "hello".getBytes(StandardCharsets.UTF_8));

    assertThat(store.find(other, attachment.id())).isEmpty();
    assertThat(store.list(other)).isEmpty();
    assertThat(store.read(other, attachment.id())).isEmpty();
  }
}
