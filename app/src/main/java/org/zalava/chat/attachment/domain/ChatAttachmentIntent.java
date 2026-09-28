package org.zalava.chat.attachment.domain;

/** How an attached file is retained: for the current task only, or durably imported. */
public enum ChatAttachmentIntent {
  TASK_ONLY,
  KNOWLEDGE_IMPORT
}
