package org.zalava.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.discovery.CapabilityGapClassification;
import org.zalava.discovery.CapabilityGapEvidence;
import org.zalava.discovery.RemoteCatalogException;
import org.zalava.discovery.RemoteModuleCandidate;
import org.zalava.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.discovery.application.port.out.CapabilityGapEvidenceStore;
import org.zalava.discovery.application.port.out.RemoteModuleCatalog;

class DefaultRemoteCapabilityDiscoveryTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:15:30Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void installedEligibleMatchSuppressesRemoteLookupEntirely() {
    AtomicInteger lookups = new AtomicInteger();
    RemoteModuleCatalog catalog =
        new RemoteModuleCatalog() {
          @Override
          public boolean configured() {
            return true;
          }

          @Override
          public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
            lookups.incrementAndGet();
            return List.of(candidate("sea-module-weather", "1.0.0", "Weather", "Weather forecast"));
          }
        };
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("forecast pollen", 3);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.INSTALLED_MATCH);
    assertThat(outcome.candidates()).isEmpty();
    assertThat(lookups.get()).isZero();
    assertThat(store.saved).isEmpty();
  }

  @Test
  void disabledCatalogIsNeverQueriedAndPersistsNothing() {
    AtomicInteger lookups = new AtomicInteger();
    RemoteModuleCatalog disabled =
        new RemoteModuleCatalog() {
          @Override
          public boolean configured() {
            return false;
          }

          @Override
          public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
            lookups.incrementAndGet();
            throw new RemoteCatalogException("must not be called");
          }
        };
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            disabled, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("forecast pollen", 0);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.DISABLED);
    assertThat(lookups.get()).isZero();
    assertThat(store.saved).isEmpty();
  }

  @Test
  void noLocalMatchYieldsDeterministicallyRankedWeakMatchEvidence() {
    RemoteModuleCatalog catalog =
        catalog(
            () ->
                List.of(
                    candidate("sea-module-zeta", "2.0.0", "Pollen helper", "Pollen helper"),
                    candidate("sea-module-alpha", "1.0.0", "Pollen tracker", "Pollen tracker")));
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("pollen", 0);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.WEAK_MATCH);
    assertThat(outcome.candidates())
        .extracting(CapabilityGapEvidence.RankedCandidate::moduleId)
        .containsExactly("sea-module-alpha", "sea-module-zeta");
    assertThat(outcome.candidates())
        .extracting(CapabilityGapEvidence.RankedCandidate::rank)
        .containsExactly(1, 2);
    assertThat(store.saved).hasSize(1);
    CapabilityGapEvidence evidence = store.saved.getFirst();
    assertThat(evidence.classification()).isEqualTo(CapabilityGapClassification.WEAK_MATCH);
    assertThat(evidence.occurredAt()).isEqualTo(NOW);
    assertThat(evidence.installedMatchCount()).isZero();
    assertThat(evidence.queryDigest()).startsWith("sha256:").hasSize(7 + 64);
    assertThat(evidence.queryDigest()).doesNotContain("pollen");
    assertThat(evidence.candidates()).hasSize(2);
  }

  @Test
  void exactModuleIdRanksAbovePartialMatches() {
    RemoteModuleCatalog catalog =
        catalog(
            () ->
                List.of(
                    candidate("sea-module-weather", "1.0.0", "Weather", "Weather forecast"),
                    candidate("sea-module-weather-extra", "1.0.0", "Extra", "Weather extra")));
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("sea module weather", 0);

    assertThat(outcome.candidates())
        .extracting(CapabilityGapEvidence.RankedCandidate::moduleId)
        .startsWith("sea-module-weather");
    assertThat(outcome.candidates().getFirst().reason()).contains("moduleId-exact");
  }

  @Test
  void policyIneligibleCandidatesAreRemovedBeforeClassification() {
    RemoteModuleCatalog catalog =
        catalog(
            () ->
                List.of(
                    candidate(
                        "sea-module-danger", "1.0.0", "Pollen shell", "Pollen shell", "shell"),
                    candidate(
                        "sea-module-danger-2",
                        "1.0.0",
                        "Pollen host",
                        "Pollen host",
                        "unrestricted-host")));
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("pollen", 0);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.NO_MATCH);
    assertThat(outcome.candidates()).isEmpty();
    assertThat(store.saved.getFirst().candidates()).isEmpty();
  }

  @Test
  void emptyRemoteCatalogYieldsPersistedNoMatchEvidence() {
    RemoteModuleCatalog catalog = catalog(() -> List.of());
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("forecast pollen", 0);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.NO_MATCH);
    assertThat(store.saved).singleElement().satisfies(this::assertSafeEvidence);
  }

  @Test
  void malformedOrUnavailableCatalogYieldsBoundedUnavailableEvidence() {
    RemoteModuleCatalog catalog =
        new RemoteModuleCatalog() {
          @Override
          public boolean configured() {
            return true;
          }

          @Override
          public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
            throw new RemoteCatalogException("index must be valid YAML " + "x".repeat(500));
          }
        };
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("forecast pollen", 0);

    assertThat(outcome.classification()).isEqualTo(CapabilityGapClassification.UNAVAILABLE);
    assertThat(outcome.candidates()).isEmpty();
    CapabilityGapEvidence evidence = store.saved.getFirst();
    assertThat(evidence.classification()).isEqualTo(CapabilityGapClassification.UNAVAILABLE);
    assertThat(evidence.detail())
        .startsWith("remote module catalog unavailable: index must be valid YAML");
    assertThat(evidence.detail()).hasSizeLessThanOrEqualTo(200);
    assertThat(evidence.candidates()).isEmpty();
    assertThat(evidence.detail()).doesNotContain("forecast", "pollen");
  }

  @Test
  void repeatedLookupIsIdempotentAndDoesNotMutateAnyInstallState() {
    RemoteModuleCatalog catalog =
        catalog(
            () -> List.of(candidate("sea-module-weather", "1.0.0", "Weather", "Pollen weather")));
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog, new RemoteCandidatePolicy(), store, CLOCK, 10);

    RemoteCapabilityDiscovery.Outcome first = discovery.discover("pollen", 0);
    RemoteCapabilityDiscovery.Outcome second = discovery.discover("pollen", 0);

    assertThat(second).isEqualTo(first);
    assertThat(store.saved).hasSize(2);
    assertThat(first.classification()).isEqualTo(CapabilityGapClassification.WEAK_MATCH);
  }

  @Test
  void boundsCandidateResultsToTheConfiguredMaximum() {
    RemoteModuleCatalog catalog =
        catalog(
            () ->
                List.of(
                    candidate("sea-module-a", "1.0.0", "Pollen a", "Pollen a"),
                    candidate("sea-module-b", "1.0.0", "Pollen b", "Pollen b"),
                    candidate("sea-module-c", "1.0.0", "Pollen c", "Pollen c")));
    RecordingStore store = new RecordingStore();
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(catalog, new RemoteCandidatePolicy(), store, CLOCK, 2);

    RemoteCapabilityDiscovery.Outcome outcome = discovery.discover("pollen", 0);

    assertThat(outcome.candidates()).hasSize(2);
    assertThat(store.saved.getFirst().candidates()).hasSize(2);
  }

  @Test
  void rejectsInvalidInputsAndBounds() {
    DefaultRemoteCapabilityDiscovery discovery =
        new DefaultRemoteCapabilityDiscovery(
            catalog(() -> List.of()), new RemoteCandidatePolicy(), new RecordingStore(), CLOCK, 10);

    assertThatThrownBy(() -> discovery.discover(" ", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> discovery.discover(null, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> discovery.discover("pollen", -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DefaultRemoteCapabilityDiscovery(
                    catalog(() -> List.of()),
                    new RemoteCandidatePolicy(),
                    new RecordingStore(),
                    CLOCK,
                    0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DefaultRemoteCapabilityDiscovery(
                    catalog(() -> List.of()),
                    new RemoteCandidatePolicy(),
                    new RecordingStore(),
                    CLOCK,
                    51))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertSafeEvidence(CapabilityGapEvidence evidence) {
    assertThat(evidence.queryDigest()).startsWith("sha256:");
    assertThat(evidence.detail()).doesNotContain("forecast", "pollen");
  }

  private static RemoteModuleCatalog catalog(
      java.util.function.Supplier<List<RemoteModuleCandidate>> supplier) {
    return new RemoteModuleCatalog() {
      @Override
      public boolean configured() {
        return true;
      }

      @Override
      public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
        return supplier.get();
      }
    };
  }

  private static RemoteModuleCandidate candidate(
      String moduleId, String version, String displayName, String description) {
    return candidate(moduleId, version, displayName, description, "read");
  }

  private static RemoteModuleCandidate candidate(
      String moduleId, String version, String displayName, String description, String permission) {
    return new RemoteModuleCandidate(
        moduleId, version, "a".repeat(64), displayName, description, List.of(permission));
  }

  private static final class RecordingStore implements CapabilityGapEvidenceStore {
    private final List<CapabilityGapEvidence> saved = new ArrayList<>();

    @Override
    public void save(CapabilityGapEvidence evidence) {
      saved.add(evidence);
    }

    @Override
    public List<CapabilityGapEvidence> recent(int limit) {
      return saved.stream().limit(limit).toList();
    }
  }
}
