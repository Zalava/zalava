package org.zalava.assistant.chat.attachment.domain;

/** Raised when an attachment is missing or not owned by the requesting actor. */
public final class ChatAttachmentNotFoundException extends RuntimeException {
  public ChatAttachmentNotFoundException(String attachmentId) {
    super("Attachment not found: " + attachmentId);
  }
}
