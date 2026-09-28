package org.zalava.content;

/** Immutable identity and version of the extractor that produced an outcome. */
public record ContentProcessor(String id, String version) {
  public ContentProcessor {
    requireText(id, "id");
    requireText(version, "version");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}
