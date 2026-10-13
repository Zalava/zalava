package org.zalava.assistant.chat.attachment.application;

/** Only host-authored safe recovery text may be exposed to Chat. */
public final class AttachmentReadingException extends IllegalStateException {
  public AttachmentReadingException(String message) {
    super(message);
  }
}
