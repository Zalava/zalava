package org.zalava.content;

/** Optional source-relative location for a normalized extraction fragment. */
public record ContentLocation(long startOffset, long endOffset, Integer pageNumber) {
  public ContentLocation {
    if (startOffset < 0) throw new IllegalArgumentException("startOffset must not be negative");
    if (endOffset < startOffset)
      throw new IllegalArgumentException("endOffset must not precede startOffset");
    if (pageNumber != null && pageNumber < 1)
      throw new IllegalArgumentException("pageNumber must be positive");
  }
}
