package org.zalava.api.extensions.content;

/** Stable failure classification that does not expose extractor implementation details. */
public enum ContentExtractionFailureCategory {
  UNSUPPORTED_MEDIA_TYPE,
  MALFORMED_INPUT,
  ENCRYPTED,
  INPUT_LIMIT_EXCEEDED,
  OUTPUT_LIMIT_EXCEEDED,
  TIMED_OUT,
  UNAVAILABLE,
  INTERNAL
}
