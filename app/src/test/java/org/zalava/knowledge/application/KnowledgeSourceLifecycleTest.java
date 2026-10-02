package org.zalava.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.*;
import org.zalava.knowledge.domain.*;

class KnowledgeSourceLifecycleTest {
  private final Actor owner = new Actor(new AccountId(UUID.randomUUID()));

  @Test
  void failedCandidateKeepsThePriorActiveDerivation() {
    var sources = new Sources(source());
    var derivations = new Derivations();
    var lifecycle = lifecycle(sources, derivations);
    var first = lifecycle.beginReprocessing(owner, sources.source.id(), "extractor", "1");
    lifecycle.completeReprocessing(owner, first, true);
    var second = lifecycle.beginReprocessing(owner, sources.source.id(), "extractor", "2");
    lifecycle.completeReprocessing(owner, second, false);

    assertThat(derivations.active(sources.source.id()))
        .contains(
            first
                .withState(DerivationState.ACTIVE, Instant.parse("2026-08-28T00:00:00Z"))
                .withPersistenceVersion(1));
    assertThat(derivations.findBySourceId(sources.source.id()))
        .extracting(KnowledgeDerivation::state)
        .containsExactly(DerivationState.ACTIVE, DerivationState.FAILED);
    assertThat(sources.source.processingState()).isEqualTo(SourceProcessingState.READY);
  }

  @Test
  void registerPersistsAnIndependentPrivateOriginalWithItsVerifiedReceipt() {
    var sources = new Sources(source());
    var blobs = new RecordingBlobs();
    var lifecycle =
        new KnowledgeSourceLifecycle(
            sources,
            new Derivations(),
            blobs,
            (id, actor) -> {},
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

    KnowledgeSource registered =
        lifecycle.register(owner, "receipt.txt", "text/plain", new byte[] {1, 2});

    assertThat(registered.visibility()).isEqualTo(KnowledgeVisibility.PRIVATE);
    assertThat(registered.sha256()).isEqualTo("a".repeat(64));
    assertThat(blobs.written).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  void hardDeleteRemovesBlobDerivationsAndSourceWhileRetainingAuditEvidence() {
    var sources = new Sources(source());
    var derivations = new Derivations();
    derivations.record(
        new KnowledgeDerivation(
            sources.source.id(),
            1,
            "extractor",
            "1",
            DerivationState.ACTIVE,
            Instant.parse("2026-08-28T00:00:00Z"),
            0));
    var blobs = new RecordingBlobs();
    var audit = new RecordingAudit();
    var lifecycle =
        new KnowledgeSourceLifecycle(
            sources,
            derivations,
            blobs,
            audit,
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    KnowledgeSourceId id = sources.source.id();

    lifecycle.hardDelete(owner, id);

    assertThat(sources.source).isNull();
    assertThat(derivations.values).isEmpty();
    assertThat(blobs.deleted).isTrue();
    assertThat(audit.deleted).isEqualTo(id);

    lifecycle.hardDelete(owner, id);
  }

  private KnowledgeSourceLifecycle lifecycle(Sources sources, Derivations derivations) {
    return new KnowledgeSourceLifecycle(
        sources,
        derivations,
        new Blobs(),
        (id, actor) -> {},
        Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
  }

  private KnowledgeSource source() {
    Instant now = Instant.parse("2026-08-28T00:00:00Z");
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        owner,
        "source.txt",
        "text/plain",
        1,
        "a".repeat(64),
        KnowledgeVisibility.PRIVATE,
        SourceProcessingState.PENDING,
        now,
        now,
        0);
  }

  private static final class Sources implements KnowledgeSourceStore {
    private KnowledgeSource source;

    Sources(KnowledgeSource source) {
      this.source = source;
    }

    public KnowledgeSource register(KnowledgeSource source) {
      return this.source = source;
    }

    public KnowledgeSource save(KnowledgeSource source) {
      return this.source = source.withVersion(source.version() + 1);
    }

    public Optional<KnowledgeSource> findById(KnowledgeSourceId id) {
      return source != null && source.id().equals(id) ? Optional.of(source) : Optional.empty();
    }

    public List<KnowledgeSource> visibleTo(Actor actor) {
      return List.of(source);
    }

    public void delete(KnowledgeSource source) {
      this.source = null;
    }
  }

  private static final class Derivations implements KnowledgeDerivationStore {
    private final Map<Long, KnowledgeDerivation> values = new TreeMap<>();

    public KnowledgeDerivation record(KnowledgeDerivation derivation) {
      values.put(derivation.version(), derivation);
      return derivation;
    }

    public KnowledgeDerivation save(KnowledgeDerivation derivation) {
      KnowledgeDerivation saved =
          derivation.withPersistenceVersion(derivation.persistenceVersion() + 1);
      values.put(saved.version(), saved);
      return saved;
    }

    public Optional<KnowledgeDerivation> active(KnowledgeSourceId id) {
      return values.values().stream().filter(d -> d.state() == DerivationState.ACTIVE).findFirst();
    }

    public List<KnowledgeDerivation> findBySourceId(KnowledgeSourceId id) {
      return List.copyOf(values.values());
    }

    public void deleteBySourceId(KnowledgeSourceId id) {
      values.clear();
    }
  }

  private static final class Blobs implements KnowledgeBlobStore {
    public BlobReceipt write(KnowledgeSourceId id, byte[] content) {
      throw new UnsupportedOperationException();
    }

    public Optional<byte[]> read(KnowledgeSourceId id) {
      return Optional.empty();
    }

    public void delete(KnowledgeSourceId id) {}
  }

  private static final class RecordingBlobs implements KnowledgeBlobStore {
    private byte[] written;
    private boolean deleted;

    public BlobReceipt write(KnowledgeSourceId id, byte[] content) {
      written = content;
      return new BlobReceipt(content.length, "a".repeat(64));
    }

    public Optional<byte[]> read(KnowledgeSourceId id) {
      return Optional.empty();
    }

    public void delete(KnowledgeSourceId id) {
      deleted = true;
    }
  }

  private static final class RecordingAudit implements KnowledgeAuditStore {
    private KnowledgeSourceId deleted;

    public void recordDeletion(KnowledgeSourceId sourceId, Actor actor) {
      deleted = sourceId;
    }
  }
}
