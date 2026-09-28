package org.zalava.knowledge.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Processor provenance and lifecycle only; extracted content belongs in later bounded ingestion
 * work.
 */
public record KnowledgeDerivation(
    KnowledgeSourceId sourceId,
    long version,
    String processorId,
    String processorVersion,
    DerivationState state,
    Instant recordedAt,
    long persistenceVersion) {
  public KnowledgeDerivation {
    Objects.requireNonNull(sourceId, "sourceId");
    if (version < 1) throw new IllegalArgumentException("version must be positive");
    requireText(processorId, "processorId");
    requireText(processorVersion, "processorVersion");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(recordedAt, "recordedAt");
    if (persistenceVersion < 0)
      throw new IllegalArgumentException("persistenceVersion must not be negative");
  }

  public KnowledgeDerivation withState(DerivationState replacement, Instant at) {
    return new KnowledgeDerivation(
        sourceId, version, processorId, processorVersion, replacement, at, persistenceVersion);
  }

  public KnowledgeDerivation withPersistenceVersion(long persistenceVersion) {
    return new KnowledgeDerivation(
        sourceId, version, processorId, processorVersion, state, recordedAt, persistenceVersion);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}
