package org.zalava.api.extensions.content;

import org.zalava.api.ZalavaServiceContract;

/** Extracts one bounded source without access to Zalava-owned storage or lifecycle state. */
@FunctionalInterface
public interface ContentExtractor {
  ZalavaServiceContract<ContentExtractor> CONTRACT =
      new ZalavaServiceContract<>("content-extractor", "1", ContentExtractor.class);

  ContentExtractionOutcome extract(ContentExtractionRequest request);
}
