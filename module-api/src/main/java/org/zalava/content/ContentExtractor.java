package org.zalava.content;

import org.zalava.ZalavaServiceContract;

/** Extracts one bounded source without access to SEA-owned storage or lifecycle state. */
@FunctionalInterface
public interface ContentExtractor {
  ZalavaServiceContract<ContentExtractor> CONTRACT =
      new ZalavaServiceContract<>("content-extractor", "1", ContentExtractor.class);

  ContentExtractionOutcome extract(ContentExtractionRequest request);
}
