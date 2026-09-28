package org.zalava.chat.attachment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.zalava.accounts.domain.Actor;

/** An owner-scoped file reference attached through the SEA conversation surface. */
public record ChatAttachment(
    String id,
    Actor owner,
    String displayName,
    String contentType,
    long byteCount,
    String sha256,
    Instant createdAt) {

  public ChatAttachment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(sha256, "sha256");
    Objects.requireNonNull(createdAt, "createdAt");
    if (!id.matches("[0-9a-fA-F-]{36}")) {
      throw new IllegalArgumentException("Attachment id must be a UUID");
    }
    if (displayName.isBlank()) {
      throw new IllegalArgumentException("Attachment name must not be blank");
    }
    if (byteCount < 0) {
      throw new IllegalArgumentException("Attachment byte count must not be negative");
    }
  }

  public static ChatAttachment create(
      Actor owner, String displayName, String contentType, long byteCount, String sha256) {
    return new ChatAttachment(
        UUID.randomUUID().toString(),
        owner,
        displayName,
        contentType,
        byteCount,
        sha256,
        Instant.now());
  }
}
