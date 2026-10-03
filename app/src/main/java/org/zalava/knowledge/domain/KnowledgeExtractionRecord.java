package org.zalava.knowledge.domain;

import java.util.Objects;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;

/** Bounded, Zalava-owned persisted outcome for one candidate derivation. */
public record KnowledgeExtractionRecord(
    KnowledgeSourceId sourceId,
    long derivationVersion,
    String text,
    ContentExtractionFailureCategory failureCategory,
    String failureDetail) {
  public KnowledgeExtractionRecord {
    Objects.requireNonNull(sourceId, "sourceId");
    if (derivationVersion < 1)
      throw new IllegalArgumentException("derivationVersion must be positive");
    if ((text == null) == (failureCategory == null)) {
      throw new IllegalArgumentException("exactly one extraction outcome must be present");
    }
    if (text != null && failureDetail != null)
      throw new IllegalArgumentException("successful extraction has no failure detail");
    if (failureDetail != null && (failureDetail.isBlank() || failureDetail.length() > 1024)) {
      throw new IllegalArgumentException("failure detail must be bounded");
    }
  }

  public static KnowledgeExtractionRecord succeeded(KnowledgeDerivation derivation, String text) {
    return new KnowledgeExtractionRecord(
        derivation.sourceId(), derivation.version(), Objects.requireNonNull(text), null, null);
  }

  public static KnowledgeExtractionRecord failed(
      KnowledgeDerivation derivation, ContentExtractionFailureCategory category, String detail) {
    return new KnowledgeExtractionRecord(
        derivation.sourceId(), derivation.version(), null, category, detail);
  }
}
