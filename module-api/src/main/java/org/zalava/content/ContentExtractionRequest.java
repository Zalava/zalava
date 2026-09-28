package org.zalava.content;

import java.util.Objects;

/** One host-created extraction request with exactly one bounded source input. */
public record ContentExtractionRequest(
    ContentSourceMetadata source, ContentSourceInput input, ContentExtractionLimits limits) {
  public ContentExtractionRequest {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(input, "input must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    if (source.byteCount() > limits.maximumInputBytes())
      throw new IllegalArgumentException("source byteCount exceeds maximumInputBytes");
    if (input.maximumBytes() > limits.maximumInputBytes())
      throw new IllegalArgumentException("input maximumBytes exceeds maximumInputBytes");
  }
}
