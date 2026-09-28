package org.zalava.content;

import java.util.Objects;

/** Immutable non-authorizing metadata for the one source supplied to an extractor. */
public record ContentSourceMetadata(
    String displayName, String mediaType, long byteCount, String sha256) {
  public ContentSourceMetadata {
    requireText(displayName, "displayName");
    requireText(mediaType, "mediaType");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount must not be negative");
    if (sha256 == null || !sha256.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(Objects.requireNonNull(name) + " must not be blank");
  }
}
