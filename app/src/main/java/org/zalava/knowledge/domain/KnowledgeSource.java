package org.zalava.knowledge.domain;

import java.time.Instant;
import java.util.Objects;
import org.zalava.identity.accounts.domain.Actor;

/**
 * Zalava-owned source metadata. The original blob and all derivations are addressed only by this
 * id.
 */
public record KnowledgeSource(
    KnowledgeSourceId id,
    Actor owner,
    String displayName,
    String contentType,
    long byteCount,
    String sha256,
    KnowledgeVisibility visibility,
    SourceProcessingState processingState,
    Instant createdAt,
    Instant updatedAt,
    long version) {
  public KnowledgeSource {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(owner, "owner");
    requireText(displayName, "displayName");
    requireText(contentType, "contentType");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount must not be negative");
    if (sha256 == null || !sha256.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(processingState, "processingState");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (version < 0) throw new IllegalArgumentException("version must not be negative");
  }

  public KnowledgeSource share(Instant at) {
    return changed(KnowledgeVisibility.GROUP_SHARED, processingState, at);
  }

  public KnowledgeSource unshare(Instant at) {
    return changed(KnowledgeVisibility.PRIVATE, processingState, at);
  }

  public KnowledgeSource requestDeletion(Instant at) {
    return changed(visibility, SourceProcessingState.DELETION_REQUESTED, at);
  }

  public KnowledgeSource deleted(Instant at) {
    return changed(KnowledgeVisibility.PRIVATE, SourceProcessingState.DELETED, at);
  }

  private KnowledgeSource changed(
      KnowledgeVisibility changedVisibility, SourceProcessingState changedState, Instant at) {
    Objects.requireNonNull(at, "at");
    return new KnowledgeSource(
        id,
        owner,
        displayName,
        contentType,
        byteCount,
        sha256,
        changedVisibility,
        changedState,
        createdAt,
        at,
        version);
  }

  public KnowledgeSource withVersion(long version) {
    return new KnowledgeSource(
        id,
        owner,
        displayName,
        contentType,
        byteCount,
        sha256,
        visibility,
        processingState,
        createdAt,
        updatedAt,
        version);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}
