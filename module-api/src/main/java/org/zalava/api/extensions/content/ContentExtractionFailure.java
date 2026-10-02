package org.zalava.api.extensions.content;

import java.util.Objects;

/** Bounded, technology-neutral failure returned by an extractor. */
public record ContentExtractionFailure(
    ContentSourceMetadata source,
    ContentProcessor processor,
    ContentExtractionFailureCategory category,
    String detail)
    implements ContentExtractionOutcome {
  public ContentExtractionFailure {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(processor, "processor must not be null");
    Objects.requireNonNull(category, "category must not be null");
    if (detail == null || detail.isBlank())
      throw new IllegalArgumentException("detail must not be blank");
    if (detail.length() > 1_024)
      throw new IllegalArgumentException("detail must not exceed 1024 characters");
  }

  public static ContentExtractionFailure forRequest(
      ContentExtractionRequest request,
      ContentProcessor processor,
      ContentExtractionFailureCategory category,
      String detail) {
    Objects.requireNonNull(request, "request must not be null");
    return new ContentExtractionFailure(request.source(), processor, category, detail);
  }
}
