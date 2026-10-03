package org.zalava.knowledge.memory.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Zalava-owned content-safety policy for durable memory promotion.
 *
 * <p>It rejects transient execution traces and obviously sensitive content before anything becomes
 * reviewable durable memory. It is a deterministic boundary: a proposing model cannot bypass it,
 * and an approval re-validates the stored content in case the policy changed since the proposal.
 */
public final class MemoryContentPolicy {

  private static final String[] SECRET_MARKERS = {
    "password",
    "passphrase",
    "api key",
    "apikey",
    "client secret",
    "access token",
    "bearer ",
    "private key"
  };
  private static final Pattern CREDENTIAL_LIKE =
      Pattern.compile(
          "(?i)(sk-[a-z0-9]{16,}|ghp_[a-z0-9]{20,}|xox[baprs]-[a-z0-9-]{10,}|[a-f0-9]{40,})");

  private MemoryContentPolicy() {}

  /**
   * Validates a durable-memory proposal.
   *
   * @throws UnsafeMemoryContentException when the scope is transient or the text looks
   *     sensitive/oversized
   */
  public static void validate(MemoryScope scope, String text) {
    if (scope == null) {
      throw new UnsafeMemoryContentException("A memory scope is required");
    }
    if (!scope.durable()) {
      throw new UnsafeMemoryContentException(
          "Transient execution memory cannot be promoted to durable memory");
    }
    if (text == null || text.isBlank()) {
      throw new UnsafeMemoryContentException("A memory proposal must contain text");
    }
    if (text.length() > MemoryProposalDraft.MAXIMUM_TEXT_LENGTH) {
      throw new UnsafeMemoryContentException(
          "A memory proposal must not exceed "
              + MemoryProposalDraft.MAXIMUM_TEXT_LENGTH
              + " characters");
    }
    String normalized = text.toLowerCase(Locale.ROOT);
    for (String marker : SECRET_MARKERS) {
      if (normalized.contains(marker)) {
        throw new UnsafeMemoryContentException(
            "A memory proposal must not contain credentials or secrets");
      }
    }
    if (CREDENTIAL_LIKE.matcher(text).find()) {
      throw new UnsafeMemoryContentException(
          "A memory proposal must not contain credentials or secrets");
    }
  }

  public static final class UnsafeMemoryContentException extends IllegalArgumentException {
    public UnsafeMemoryContentException(String message) {
      super(message);
    }
  }
}
