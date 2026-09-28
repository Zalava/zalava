package org.zalava.content;

import org.zalava.SeaServiceContract;

/** Extracts one bounded source without access to SEA-owned storage or lifecycle state. */
@FunctionalInterface
public interface ContentExtractor {
  SeaServiceContract<ContentExtractor> CONTRACT =
      new SeaServiceContract<>("content-extractor", "1", ContentExtractor.class);

  ContentExtractionOutcome extract(ContentExtractionRequest request);
}
