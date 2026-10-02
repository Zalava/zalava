package org.zalava.knowledge.skills.domain;

import java.util.Objects;

/**
 * Storage-independent origin of a discovered skill. {@code source} names the origin kind (for
 * example {@code local} or {@code remote}); {@code reference} is an opaque location such as a
 * directory or catalogue identity.
 */
public record SkillProvenance(String source, String reference) {

  public static final String LOCAL_SOURCE = "local";
  public static final String REMOTE_SOURCE = "remote";

  public SkillProvenance {
    source = requireText(source, "source", 64);
    reference = requireText(reference, "reference", 512);
  }

  public static SkillProvenance local(String reference) {
    return new SkillProvenance(LOCAL_SOURCE, reference);
  }

  public static SkillProvenance remote(String reference) {
    return new SkillProvenance(REMOTE_SOURCE, reference);
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
}
