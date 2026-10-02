package org.zalava.assistant.chat.attachment.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.assistant.chat.attachment.domain.ChatAttachment;
import org.zalava.identity.accounts.domain.Actor;

/** Owner-scoped persistence for task-only chat attachments. */
public interface ChatAttachmentStore {
  ChatAttachment save(Actor actor, ChatAttachment attachment, byte[] content);

  Optional<ChatAttachment> find(Actor actor, String attachmentId);

  List<ChatAttachment> list(Actor actor);

  Optional<byte[]> read(Actor actor, String attachmentId);

  void delete(Actor actor, String attachmentId);
}
