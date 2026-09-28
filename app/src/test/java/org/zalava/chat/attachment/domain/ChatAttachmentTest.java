package org.zalava.chat.attachment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class ChatAttachmentTest {

  @Test
  void createsAnOwnerScopedReference() {
    Actor owner = new Actor(AccountId.newId());

    ChatAttachment attachment = ChatAttachment.create(owner, "notes.txt", "text/plain", 5, "abc");

    assertThat(attachment.owner()).isEqualTo(owner);
    assertThat(attachment.displayName()).isEqualTo("notes.txt");
    assertThat(UUID.fromString(attachment.id())).isNotNull();
  }

  @Test
  void rejectsInvalidIdentityNameAndByteCount() {
    Actor owner = new Actor(AccountId.newId());
    assertThatThrownBy(
            () ->
                new ChatAttachment(
                    "not-a-uuid", owner, "notes.txt", "text/plain", 5, "abc", Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ChatAttachment(
                    UUID.randomUUID().toString(),
                    owner,
                    " ",
                    "text/plain",
                    5,
                    "abc",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ChatAttachment(
                    UUID.randomUUID().toString(),
                    owner,
                    "notes.txt",
                    "text/plain",
                    -1,
                    "abc",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
