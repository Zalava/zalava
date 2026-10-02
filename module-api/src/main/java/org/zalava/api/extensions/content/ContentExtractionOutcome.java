package org.zalava.api.extensions.content;

/** A source-correlated successful extraction or typed failure. */
public sealed interface ContentExtractionOutcome
    permits ContentExtractionFailure, ContentExtractionResult {
  ContentSourceMetadata source();

  ContentProcessor processor();
}
