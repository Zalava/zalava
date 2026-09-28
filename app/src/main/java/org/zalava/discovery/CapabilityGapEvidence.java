package org.zalava.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Actor-safe capability-gap evidence. It stores a normalized query digest and never the raw private
 * prompt, context or authorization material.
 */
public record CapabilityGapEvidence(
    String queryDigest,
    Instant occurredAt,
    int installedMatchCount,
    CapabilityGapClassification classification,
    String detail,
    List<RankedCandidate> candidates) {

  public CapabilityGapEvidence {
    queryDigest = requireText(queryDigest, "queryDigest");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    classification = Objects.requireNonNull(classification, "classification must not be null");
    if (installedMatchCount < 0) {
      throw new IllegalArgumentException("installedMatchCount must not be negative");
    }
    detail = detail == null ? "" : detail;
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
  }

  /** One deterministically ranked recommendation; a weak match is never an installation. */
  public record RankedCandidate(
      String moduleId, String version, String digest, int rank, String reason) {
    public RankedCandidate {
      moduleId = requireText(moduleId, "moduleId");
      version = requireText(version, "version");
      digest = requireText(digest, "digest");
      if (rank < 1) {
        throw new IllegalArgumentException("rank must be positive");
      }
      reason = reason == null ? "" : reason;
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Capability gap evidence " + field + " must not be blank");
    }
    return value;
  }
}
