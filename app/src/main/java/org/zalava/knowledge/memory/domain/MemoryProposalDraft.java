package org.zalava.knowledge.memory.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Bounded, model-supplied proposal input. SEA validates scope, length and content safety before a
 * proposal is persisted; a draft is never durable memory on its own.
 */
public record MemoryProposalDraft(
    MemoryScope scope, String text, Map<String, String> metadata, String reference) {

  public static final int MAXIMUM_TEXT_LENGTH = 2000;
  public static final int MAXIMUM_REFERENCE_LENGTH = 200;

  public MemoryProposalDraft {
    Objects.requireNonNull(scope, "scope must not be null");
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("text must not be blank");
    }
    text = text.strip();
    if (text.length() > MAXIMUM_TEXT_LENGTH) {
      throw new IllegalArgumentException(
          "text must not exceed " + MAXIMUM_TEXT_LENGTH + " characters");
    }
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    reference = normalizeReference(reference);
  }

  public static MemoryProposalDraft of(MemoryScope scope, String text) {
    return new MemoryProposalDraft(scope, text, Map.of(), null);
  }

  private static String normalizeReference(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > MAXIMUM_REFERENCE_LENGTH) {
      throw new IllegalArgumentException(
          "reference must not exceed " + MAXIMUM_REFERENCE_LENGTH + " characters");
    }
    return normalized;
  }
}
