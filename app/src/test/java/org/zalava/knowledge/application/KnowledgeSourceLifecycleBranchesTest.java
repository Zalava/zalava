package org.zalava.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;

/**
 * Covers the knowledge lifecycle branches left out of the base test: visibility changes for both
 * outcomes, sharing evidence, failed-completion fallbacks, the metric barrier, and ownership
 * enforcement on every entry point.
 */
class KnowledgeSourceLifecycleBranchesTest {

  private final Actor owner = new Actor(new AccountId(UUID.randomUUID()));
  private final Actor intruder = new Actor(new AccountId(UUID.randomUUID()));
  private final RecordingBlobs blobs = new RecordingBlobs();
  private final RecordingAudit audit = new RecordingAudit();
  private final Sources sources = new Sources();
  private final Derivations derivations = new Derivations();
  private final FlakyMetrics metrics = new FlakyMetrics();

  private KnowledgeSourceLifecycle lifecycle() {
    return new KnowledgeSourceLifecycle(
        sources,
        derivations,
        blobs,
        audit,
        Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC),
        metrics);
  }

  @Test
  void shareAndUnsharePersistVisibilityWithAuditEvidence() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();

    KnowledgeSource shared =
        lifecycle.changeVisibility(owner, id, KnowledgeVisibility.GROUP_SHARED);
    assertThat(shared.visibility()).isEqualTo(KnowledgeVisibility.GROUP_SHARED);
    assertThat(shared.updatedAt()).isEqualTo(Instant.parse("2026-08-28T00:00:00Z"));
    assertThat(metrics.events)
        .containsExactly(
            "knowledgeLifecycle:register:succeeded", "knowledgeLifecycle:share:succeeded");

    KnowledgeSource unshared = lifecycle.changeVisibility(owner, id, KnowledgeVisibility.PRIVATE);
    assertThat(unshared.visibility()).isEqualTo(KnowledgeVisibility.PRIVATE);
    assertThat(metrics.events)
        .containsExactly(
            "knowledgeLifecycle:register:succeeded",
            "knowledgeLifecycle:share:succeeded",
            "knowledgeLifecycle:unshare:succeeded");
  }

  @Test
  void changeVisibilityRefusesOtherActorsAndUnknownSources() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();

    assertThatThrownBy(
            () -> lifecycle.changeVisibility(intruder, id, KnowledgeVisibility.GROUP_SHARED))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                lifecycle.changeVisibility(
                    owner, KnowledgeSourceId.create(), KnowledgeVisibility.GROUP_SHARED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Knowledge source not found");
  }

  @Test
  void registerRollsBackTheWrittenBlobWhenPersistenceFails() {
    Sources failing =
        new Sources() {
          @Override
          public org.zalava.knowledge.domain.KnowledgeSource register(
              org.zalava.knowledge.domain.KnowledgeSource source) {
            throw new IllegalStateException("registry unavailable");
          }
        };
    var lifecycle =
        new KnowledgeSourceLifecycle(
            failing, derivations, blobs, audit, Clock.systemUTC(), metrics);

    assertThatThrownBy(() -> lifecycle.register(owner, "x.txt", "text/plain", new byte[] {9}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("registry unavailable");
    assertThat(blobs.deleted).isTrue();
    assertThat(metrics.events).isEmpty();
  }

  @Test
  void aFailedCompletionFallsBackToFailedStateWhenNoActiveDerivationExists() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();
    KnowledgeDerivation candidate = lifecycle.beginReprocessing(owner, id, "extractor", "1");

    lifecycle.completeReprocessing(owner, candidate, false);

    assertThat(derivations.findBySourceId(id))
        .extracting(KnowledgeDerivation::state)
        .containsExactly(DerivationState.FAILED);
  }

  @Test
  void completionRefusesNonCandidateDerivationsAndForeignActors() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();
    KnowledgeDerivation candidate = lifecycle.beginReprocessing(owner, id, "extractor", "1");

    assertThatThrownBy(() -> lifecycle.completeReprocessing(intruder, candidate, true))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    lifecycle.completeReprocessing(owner, candidate, true);
    KnowledgeDerivation active =
        derivations.findBySourceId(id).stream()
            .filter(derivation -> derivation.state() == DerivationState.ACTIVE)
            .findFirst()
            .orElseThrow();
    assertThatThrownBy(() -> lifecycle.completeReprocessing(owner, active, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only a candidate derivation can complete");
  }

  @Test
  void hardDeleteRejectsOtherActorsAndRecordsNothing() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();

    assertThatThrownBy(() -> lifecycle.hardDelete(intruder, id))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
        .hasMessage("Knowledge source is not owned by actor");
    assertThat(sources.get(id)).isNotNull();
    assertThat(audit.deleted).isNull();
  }

  @Test
  void metricsFailuresNeverBreakTheLifecycle() {
    var lifecycle = lifecycle();
    KnowledgeSourceId id =
        lifecycle.register(owner, "notes.txt", "text/plain", new byte[] {1}).id();

    metrics.explode = true;

    KnowledgeSource shared =
        lifecycle.changeVisibility(owner, id, KnowledgeVisibility.GROUP_SHARED);
    assertThat(shared.visibility()).isEqualTo(KnowledgeVisibility.GROUP_SHARED);
  }

  private static final class FlakyMetrics
      implements org.zalava.observability.application.port.out.OperationalMetrics {
    final java.util.List<String> events = new java.util.ArrayList<>();
    boolean explode;

    @Override
    public void knowledgeLifecycle(String operation, String outcome) {
      if (explode) {
        throw new IllegalStateException("metrics backend down");
      }
      events.add("knowledgeLifecycle:" + operation + ":" + outcome);
    }
  }

  private static class RecordingBlobs
      implements org.zalava.knowledge.application.port.out.KnowledgeBlobStore {
    final java.util.List<Byte> written = new java.util.ArrayList<>();
    boolean deleted;

    @Override
    public BlobReceipt write(KnowledgeSourceId sourceId, byte[] content) {
      for (byte value : content) {
        written.add(value);
      }
      return new BlobReceipt(content.length, "a".repeat(64));
    }

    @Override
    public java.util.Optional<byte[]> read(KnowledgeSourceId sourceId) {
      return java.util.Optional.empty();
    }

    @Override
    public void delete(KnowledgeSourceId sourceId) {
      deleted = true;
    }
  }

  private static final class RecordingAudit
      implements org.zalava.knowledge.application.port.out.KnowledgeAuditStore {
    KnowledgeSourceId deleted;

    @Override
    public void recordDeletion(KnowledgeSourceId sourceId, Actor actor) {
      deleted = sourceId;
    }
  }

  private static class Sources
      implements org.zalava.knowledge.application.port.out.KnowledgeSourceStore {
    private final java.util.Map<KnowledgeSourceId, org.zalava.knowledge.domain.KnowledgeSource>
        values = new java.util.HashMap<>();

    @Override
    public org.zalava.knowledge.domain.KnowledgeSource register(
        org.zalava.knowledge.domain.KnowledgeSource source) {
      values.put(source.id(), source);
      return source;
    }

    @Override
    public org.zalava.knowledge.domain.KnowledgeSource save(
        org.zalava.knowledge.domain.KnowledgeSource source) {
      values.put(source.id(), source);
      return source;
    }

    @Override
    public java.util.Optional<org.zalava.knowledge.domain.KnowledgeSource> findById(
        KnowledgeSourceId id) {
      return java.util.Optional.ofNullable(values.get(id));
    }

    @Override
    public java.util.List<org.zalava.knowledge.domain.KnowledgeSource> visibleTo(Actor actor) {
      return java.util.List.copyOf(values.values());
    }

    @Override
    public void delete(org.zalava.knowledge.domain.KnowledgeSource source) {
      values.remove(source.id());
    }

    org.zalava.knowledge.domain.KnowledgeSource get(KnowledgeSourceId id) {
      return values.get(id);
    }
  }

  private static final class Derivations
      implements org.zalava.knowledge.application.port.out.KnowledgeDerivationStore {
    final java.util.Map<KnowledgeSourceId, java.util.List<KnowledgeDerivation>> values =
        new java.util.HashMap<>();

    @Override
    public KnowledgeDerivation record(KnowledgeDerivation derivation) {
      values
          .computeIfAbsent(derivation.sourceId(), ignored -> new java.util.ArrayList<>())
          .add(derivation);
      return derivation;
    }

    @Override
    public KnowledgeDerivation save(KnowledgeDerivation derivation) {
      java.util.List<KnowledgeDerivation> list = values.get(derivation.sourceId());
      if (list != null) {
        for (int index = 0; index < list.size(); index++) {
          if (list.get(index).version() == derivation.version()) {
            list.set(index, derivation);
          }
        }
      }
      return derivation;
    }

    @Override
    public java.util.List<KnowledgeDerivation> findBySourceId(KnowledgeSourceId sourceId) {
      return java.util.List.copyOf(values.getOrDefault(sourceId, java.util.List.of()));
    }

    @Override
    public java.util.Optional<KnowledgeDerivation> active(KnowledgeSourceId sourceId) {
      return findBySourceId(sourceId).stream()
          .filter(derivation -> derivation.state() == DerivationState.ACTIVE)
          .findFirst();
    }

    @Override
    public void deleteBySourceId(KnowledgeSourceId sourceId) {
      values.remove(sourceId);
    }
  }
}
