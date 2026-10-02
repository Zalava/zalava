package org.zalava.api.extensions.content;

/** Host-selected bounds that apply to one extraction request and its returned result. */
public record ContentExtractionLimits(
    long maximumInputBytes,
    int maximumTextCharacters,
    int maximumMetadataEntries,
    int maximumMetadataValueCharacters,
    int maximumLocations) {
  public ContentExtractionLimits {
    if (maximumInputBytes < 1)
      throw new IllegalArgumentException("maximumInputBytes must be positive");
    if (maximumTextCharacters < 1)
      throw new IllegalArgumentException("maximumTextCharacters must be positive");
    if (maximumMetadataEntries < 0)
      throw new IllegalArgumentException("maximumMetadataEntries must not be negative");
    if (maximumMetadataValueCharacters < 1)
      throw new IllegalArgumentException("maximumMetadataValueCharacters must be positive");
    if (maximumLocations < 0)
      throw new IllegalArgumentException("maximumLocations must not be negative");
  }
}
