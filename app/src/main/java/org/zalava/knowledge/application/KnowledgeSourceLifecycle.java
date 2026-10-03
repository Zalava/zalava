package org.zalava.knowledge.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeAuditStore;
import org.zalava.knowledge.application.port.out.KnowledgeBlobStore;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeOwnershipDenied;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;

/**
 * Coordinates Zalava-owned lifecycle changes; no processor module gets direct storage authority.
 */
public class KnowledgeSourceLifecycle {
  private final KnowledgeSourceStore sources;
  private final KnowledgeDerivationStore derivations;
  private final KnowledgeBlobStore blobs;
  private final KnowledgeAuditStore audit;
  private final Clock clock;
  private final OperationalMetrics metrics;

  public KnowledgeSourceLifecycle(
      KnowledgeSourceStore sources,
      KnowledgeDerivationStore derivations,
      KnowledgeBlobStore blobs,
      KnowledgeAuditStore audit,
      Clock clock) {
    this(sources, derivations, blobs, audit, clock, OperationalMetrics.NOOP);
  }

  public KnowledgeSourceLifecycle(
      KnowledgeSourceStore sources,
      KnowledgeDerivationStore derivations,
      KnowledgeBlobStore blobs,
      KnowledgeAuditStore audit,
      Clock clock,
      OperationalMetrics metrics) {
    this.sources = sources;
    this.derivations = derivations;
    this.blobs = blobs;
    this.audit = audit;
    this.clock = clock;
    this.metrics = metrics;
  }

  public KnowledgeSource changeVisibility(
      Actor actor, KnowledgeSourceId id, KnowledgeVisibility visibility) {
    KnowledgeSource source = owned(actor, id);
    KnowledgeSource changed =
        sources.save(
            visibility == KnowledgeVisibility.GROUP_SHARED
                ? source.share(Instant.now(clock))
                : source.unshare(Instant.now(clock)));
    observe(visibility == KnowledgeVisibility.GROUP_SHARED ? "share" : "unshare", "succeeded");
    return changed;
  }

  /**
   * Registers one independently retained original; equal content is never deduplicated across
   * actors.
   */
  public KnowledgeSource register(
      Actor actor, String displayName, String contentType, byte[] original) {
    Objects.requireNonNull(actor, "actor");
    KnowledgeSourceId id = KnowledgeSourceId.create();
    KnowledgeBlobStore.BlobReceipt receipt = blobs.write(id, original);
    Instant now = Instant.now(clock);
    try {
      KnowledgeSource registered =
          sources.register(
              new KnowledgeSource(
                  id,
                  actor,
                  displayName,
                  contentType,
                  receipt.byteCount(),
                  receipt.sha256(),
                  KnowledgeVisibility.PRIVATE,
                  SourceProcessingState.PENDING,
                  now,
                  now,
                  0));
      observe("register", "succeeded");
      return registered;
    } catch (RuntimeException ex) {
      blobs.delete(id);
      throw ex;
    }
  }

  /** Authorizes an owner-only operation without exposing source data to a module. */
  public KnowledgeSource requireOwned(Actor actor, KnowledgeSourceId id) {
    return owned(actor, id);
  }

  public void cancelReprocessing(Actor actor, KnowledgeSourceId id) {
    KnowledgeSource source = owned(actor, id);
    if (source.processingState() != SourceProcessingState.DELETION_REQUESTED
        && source.processingState() != SourceProcessingState.DELETED) {
      sources.save(withProcessingState(source, SourceProcessingState.CANCELLED));
      observe("reprocess", "cancelled");
    }
  }

  public KnowledgeDerivation beginReprocessing(
      Actor actor, KnowledgeSourceId id, String processorId, String processorVersion) {
    KnowledgeSource source = owned(actor, id);
    long version =
        derivations.findBySourceId(id).stream()
                .mapToLong(KnowledgeDerivation::version)
                .max()
                .orElse(0)
            + 1;
    sources.save(withProcessingState(source, SourceProcessingState.PROCESSING));
    return derivations.record(
        new KnowledgeDerivation(
            id,
            version,
            processorId,
            processorVersion,
            DerivationState.CANDIDATE,
            Instant.now(clock),
            0));
  }

  public void completeReprocessing(Actor actor, KnowledgeDerivation candidate, boolean successful) {
    KnowledgeSource source = owned(actor, candidate.sourceId());
    if (candidate.state() != DerivationState.CANDIDATE)
      throw new IllegalArgumentException("Only a candidate derivation can complete");
    Instant now = Instant.now(clock);
    if (successful) {
      derivations
          .active(candidate.sourceId())
          .ifPresent(active -> derivations.save(active.withState(DerivationState.REPLACED, now)));
      derivations.save(candidate.withState(DerivationState.ACTIVE, now));
      sources.save(withProcessingState(source, SourceProcessingState.READY));
    } else {
      derivations.save(candidate.withState(DerivationState.FAILED, now));
      sources.save(
          withProcessingState(
              source,
              derivations.active(candidate.sourceId()).isPresent()
                  ? SourceProcessingState.READY
                  : SourceProcessingState.FAILED));
    }
  }

  /** Idempotently resumes a confirmed deletion after any earlier partial failure. */
  public void hardDelete(Actor actor, KnowledgeSourceId id) {
    Objects.requireNonNull(actor, "actor");
    KnowledgeSource source = sources.findById(id).orElse(null);
    if (source == null) return;
    if (!source.owner().equals(actor))
      throw new KnowledgeOwnershipDenied("Knowledge source is not owned by actor");
    KnowledgeSource deletionRequested = sources.save(source.requestDeletion(Instant.now(clock)));
    blobs.delete(id);
    derivations.deleteBySourceId(id);
    audit.recordDeletion(id, actor);
    sources.delete(deletionRequested);
    observe("delete", "succeeded");
  }

  private void observe(String operation, String outcome) {
    try {
      metrics.knowledgeLifecycle(operation, outcome);
    } catch (RuntimeException ignored) {
      /* metrics must not affect knowledge lifecycle */
    }
  }

  private KnowledgeSource owned(Actor actor, KnowledgeSourceId id) {
    Objects.requireNonNull(actor, "actor");
    KnowledgeSource source =
        sources
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));
    if (!source.owner().equals(actor))
      throw new KnowledgeOwnershipDenied("Knowledge source is not owned by actor");
    return source;
  }

  private KnowledgeSource withProcessingState(KnowledgeSource source, SourceProcessingState state) {
    return new KnowledgeSource(
        source.id(),
        source.owner(),
        source.displayName(),
        source.contentType(),
        source.byteCount(),
        source.sha256(),
        source.visibility(),
        state,
        source.createdAt(),
        Instant.now(clock),
        source.version());
  }
}
