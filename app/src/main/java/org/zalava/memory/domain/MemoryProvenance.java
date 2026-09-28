package org.zalava.memory.domain;

import java.util.Objects;

/**
 * Storage-independent origin of a durable memory record. {@code source} names the SEA surface that
 * produced the record (for example {@code user}, {@code task}, {@code import} or {@code legacy});
 * {@code reference} optionally points at a run, task or import identity.
 */
public record MemoryProvenance(String source, String reference) {

  public static final String LEGACY_SOURCE = "legacy";
  private static final int MAXIMUM_SOURCE_LENGTH = 64;
  private static final int MAXIMUM_REFERENCE_LENGTH = 200;

  public MemoryProvenance {
    source = requireText(source, "source", MAXIMUM_SOURCE_LENGTH);
    reference = normalize(reference, MAXIMUM_REFERENCE_LENGTH);
  }

  /** Provenance for records persisted before memory provenance existed. */
  public static MemoryProvenance legacy() {
    return new MemoryProvenance(LEGACY_SOURCE, null);
  }

  public static MemoryProvenance of(String source) {
    return new MemoryProvenance(source, null);
  }

  public static MemoryProvenance of(String source, String reference) {
    return new MemoryProvenance(source, reference);
  }

  private static String requireText(String value, String field, int maximumLength) {
    Objects.requireNonNull(value, field + " must not be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    if (normalized.length() > maximumLength) {
      throw new IllegalArgumentException(
          field + " must not exceed " + maximumLength + " characters");
    }
    return normalized;
  }

  private static String normalize(String value, int maximumLength) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > maximumLength) {
      throw new IllegalArgumentException(
          "reference must not exceed " + maximumLength + " characters");
    }
    return normalized;
  }
}
