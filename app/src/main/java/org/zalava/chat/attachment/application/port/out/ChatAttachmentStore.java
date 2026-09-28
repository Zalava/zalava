package org.zalava.chat.attachment.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.attachment.domain.ChatAttachment;

/** Owner-scoped persistence for task-only chat attachments. */
public interface ChatAttachmentStore {
  ChatAttachment save(Actor actor, ChatAttachment attachment, byte[] content);

  Optional<ChatAttachment> find(Actor actor, String attachmentId);

  List<ChatAttachment> list(Actor actor);

  Optional<byte[]> read(Actor actor, String attachmentId);

  void delete(Actor actor, String attachmentId);
}
