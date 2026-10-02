package org.zalava.api.extensions.content;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Normalized, bounded extraction content whose source always matches its request. */
public record ContentExtractionResult(
    ContentSourceMetadata source,
    ContentProcessor processor,
    String text,
    Map<String, List<String>> metadata,
    List<ContentLocation> locations)
    implements ContentExtractionOutcome {
  public ContentExtractionResult {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(processor, "processor must not be null");
    Objects.requireNonNull(text, "text must not be null");
    metadata = copyMetadata(metadata);
    locations = locations == null ? List.of() : List.copyOf(locations);
  }

  public static ContentExtractionResult forRequest(
      ContentExtractionRequest request,
      ContentProcessor processor,
      String text,
      Map<String, List<String>> metadata,
      List<ContentLocation> locations) {
    Objects.requireNonNull(request, "request must not be null");
    ContentExtractionResult result =
        new ContentExtractionResult(request.source(), processor, text, metadata, locations);
    validateBounds(result, request.limits());
    return result;
  }

  private static void validateBounds(
      ContentExtractionResult result, ContentExtractionLimits limits) {
    if (result.text().length() > limits.maximumTextCharacters())
      throw new IllegalArgumentException("text exceeds maximumTextCharacters");
    if (result.metadata().size() > limits.maximumMetadataEntries())
      throw new IllegalArgumentException("metadata exceeds maximumMetadataEntries");
    if (result.locations().size() > limits.maximumLocations())
      throw new IllegalArgumentException("locations exceeds maximumLocations");
    for (Map.Entry<String, List<String>> entry : result.metadata().entrySet()) {
      if (entry.getKey().isBlank())
        throw new IllegalArgumentException("metadata key must not be blank");
      for (String value : entry.getValue()) {
        if (value == null || value.length() > limits.maximumMetadataValueCharacters())
          throw new IllegalArgumentException(
              "metadata value exceeds maximumMetadataValueCharacters");
      }
    }
  }

  private static Map<String, List<String>> copyMetadata(Map<String, List<String>> metadata) {
    if (metadata == null || metadata.isEmpty()) return Map.of();
    return metadata.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }
}
