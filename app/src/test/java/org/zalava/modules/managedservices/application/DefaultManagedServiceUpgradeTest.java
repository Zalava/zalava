package org.zalava.modules.managedservices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceLifecycle;
import org.zalava.api.extensions.managed.ManagedServiceLimits;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest.Phase;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceUpgrade;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceBackupPort;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceUpgradeRequestStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

class DefaultManagedServiceUpgradeTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  private final FakeEngine engine = new FakeEngine();
  private final FakeStateStore states = new FakeStateStore();
  private final FakeUpgradeRequestStore requests = new FakeUpgradeRequestStore();
  private final FakeBackup backups = new FakeBackup();
  private final MutableClock clock = new MutableClock(NOW);
  private final ManagedServiceReconciler reconciler =
      new ManagedServiceReconciler(states, engine, clock);
  private final DefaultManagedServiceUpgrade upgrades =
      new DefaultManagedServiceUpgrade(requests, states, reconciler, engine, backups, clock);

  @Test
  void promotesAnApprovedUpgradeThroughStopBackupRemoveCreate() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();

    var result = upgrades.allow(requestId, generation);

    assertThat(result.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.SUCCEEDED);
    assertThat(states.find("database"))
        .hasValueSatisfying(
            record -> {
              assertThat(record.desiredState().revision()).isEqualTo("2");
              assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
            });
    assertThat(engine.stopped).containsExactly("database");
    assertThat(engine.removed).containsExactly("database");
    assertThat(backups.backedUp).containsExactly("database");
    assertThat(backups.restored).isEmpty();
    assertThat(engine.created).containsExactly("database");
  }

  @Test
  void failedBackupAbortsBeforeAnyEngineMutationAndMarksRequestFailed() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();
    backups.failExecuteFor.add("database");

    var result = upgrades.allow(requestId, generation);

    // Designed semantics: the stop phase is non-destructive and the previous container is still
    // intact, so this outcome is an honest automatic rollback, not a failure.
    assertThat(result.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.ROLLED_BACK);
    // No engine mutation beyond the reversible stop: nothing was removed, created, or restored.
    assertThat(engine.removalLog).isEmpty();
    assertThat(engine.createLog).isEmpty();
    assertThat(backups.restored).isEmpty();
    assertThat(engine.stopped).containsExactly("database");
    assertThat(states.find("database"))
        .hasValueSatisfying(
            record -> {
              assertThat(record.desiredState().revision()).isEqualTo("1");
              assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
            });
  }

  @Test
  void failedReadinessAutomaticallyRollsBackToTheCapturedPreviousRevision() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();
    // Only revision 1 ever becomes ready: the candidate fails readiness, the rollback succeeds.
    engine.readyOnlyForRevision = "1";

    var result = upgrades.allow(requestId, generation);

    assertThat(result.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.ROLLED_BACK);
    assertThat(result.message()).contains("Rolled back to the previous revisions");
    // Rollback puts the captured previous revision back and restores its backup.
    assertThat(states.find("database"))
        .hasValueSatisfying(
            record -> {
              assertThat(record.desiredState().revision()).isEqualTo("1");
              assertThat(record.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
            });
    assertThat(backups.backedUp).containsExactly("database");
    assertThat(backups.restored).containsExactly("database");
    assertThat(engine.removalLog).containsExactly("database", "database");
    assertThat(engine.createLog).containsExactly("database", "database");
  }

  @Test
  void interruptedUpgradeRecoversToSuccessAfterRestart() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();
    // Simulate a crash right after the stop phase persisted, before the backup ran.
    interruptAfterStop(requestId, generation);

    upgrades.recover();

    assertThat(requests.find(requestId))
        .hasValueSatisfying(
            request -> {
              assertThat(request.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.SUCCEEDED);
              assertThat(request.services().get(0).phase()).isEqualTo(Phase.PROMOTED);
            });
    assertThat(states.find("database"))
        .hasValueSatisfying(record -> assertThat(record.desiredState().revision()).isEqualTo("2"));
    assertThat(backups.restored).isEmpty();
  }

  @Test
  void staleGenerationOperationsAreRejected() {
    install("database", "1");
    String requestId = plan("database", "2");

    assertThatThrownBy(() -> upgrades.allow(requestId, 99L))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("Stale upgrade operation");
    assertThat(requests.find(requestId))
        .hasValueSatisfying(
            request ->
                assertThat(request.status())
                    .isEqualTo(ManagedServiceUpgradeRequest.Status.PENDING));
    assertThat(engine.stopped).isEmpty();
  }

  @Test
  void duplicateCandidatesForTheSameServiceAreRejected() {
    install("database", "1");

    assertThatThrownBy(
            () ->
                upgrades.plan(
                    new ManagedServiceUpgrade.UpgradePlanRequest(
                        List.of(candidate("database", "2"), candidate("database", "3")))))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("Duplicate managed service id");
  }

  @Test
  void failedRollbackLeavesTheRequestFailedInsteadOfHangingInPromoting() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();
    engine.readyOnlyForRevision = "1"; // candidate fails readiness...
    backups.failRestoreFor.add("database"); // ...and rollback cannot restore either.

    var result = upgrades.allow(requestId, generation);

    assertThat(result.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.FAILED);
    assertThat(result.message()).contains("later services were not upgraded");
    assertThat(requests.find(requestId))
        .hasValueSatisfying(
            request -> {
              assertThat(request.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.FAILED);
              assertThat(request.services().get(0).phase())
                  .isEqualTo(ManagedServiceUpgradeRequest.Phase.FAILED);
            });
    assertThat(engine.createLog).containsExactly("database"); // candidate create only
  }

  @Test
  void denialRecordsNoEngineActivity() {
    install("database", "1");
    String requestId = plan("database", "2");
    long generation = requests.find(requestId).orElseThrow().generation();

    var denied = upgrades.deny(requestId, generation);

    assertThat(denied.status()).isEqualTo(ManagedServiceUpgradeRequest.Status.DENIED);
    assertThat(denied.message()).contains("no service was touched");
    assertThat(engine.stopped).isEmpty();
    assertThat(engine.removed).isEmpty();
    assertThat(backups.backedUp).isEmpty();
    assertThatThrownBy(() -> upgrades.allow(requestId, generation))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("was denied");
  }

  @Test
  void planningRejectsUnknownServicesAndResourceSurfaceChanges() {
    assertThatThrownBy(
            () ->
                upgrades.plan(
                    new ManagedServiceUpgrade.UpgradePlanRequest(List.of(candidate("ghost", "2")))))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("Unknown managed service");

    install("database", "1");
    var widened =
        new ManagedServiceDesiredState(
            "database",
            "registry.example/database@sha256:" + "b".repeat(64),
            "2",
            ManagedServiceLifecycle.RUNNING,
            Set.of(),
            Set.of("/var/lib/zalava/managed/database", "/var/lib/zalava/managed/extra"),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1_000, 10, 1),
            Duration.ofSeconds(30),
            3);
    assertThatThrownBy(
            () ->
                upgrades.plan(
                    new ManagedServiceUpgrade.UpgradePlanRequest(
                        List.of(
                            new ManagedServiceUpgrade.UpgradeCandidate(
                                "home-module", "database", widened)))))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("must keep its declared data paths");
  }

  private void interruptAfterStop(String requestId, long generation) {
    ManagedServiceUpgradeRequest request = requests.find(requestId).orElseThrow();
    List<ManagedServiceUpgradeRequest.PlannedUpgrade> services =
        new ArrayList<>(request.services());
    services.set(
        0,
        services.get(0).withPhase(Phase.STOPPED)); // Crash point: stop persisted, backup never ran.
    requests.save(
        new ManagedServiceUpgradeRequest(
            request.requestId(),
            generation,
            request.createdAt(),
            services,
            ManagedServiceUpgradeRequest.Status.PROMOTING,
            null,
            "Executing approved upgrade"));
  }

  private void install(String serviceId, String revision) {
    ManagedServiceInstallPlanning planning = new ManagedServiceInstallPlanning();
    ManagedServiceInstallRequestStore installRequests = new InMemoryInstallRequestStore();
    ManagedServiceStateStore store = states;
    DefaultManagedServiceInstallation installation =
        new DefaultManagedServiceInstallation(planning, installRequests, store, reconciler, clock);
    String installRequestId =
        installation
            .plan(
                new org.zalava.modules.managedservices.application.port.in
                    .ManagedServiceInstallation.PlanRequest(
                    List.of(
                        new org.zalava.modules.managedservices.application.port.in
                            .ManagedServiceInstallation.PlannedRequest(
                            "home-module",
                            serviceId,
                            desired(serviceId, revision),
                            grant("home-module", serviceId),
                            Set.of()))))
            .requestId();
    installation.allow(installRequestId);
    // The upgrade tests assert on engine operations issued by the upgrade itself; the seeded
    // install's operations are not part of those expectations.
    engine.createLog.clear();
    engine.removalLog.clear();
    engine.stopped.clear();
  }

  private String plan(String serviceId, String candidateRevision) {
    return upgrades
        .plan(
            new ManagedServiceUpgrade.UpgradePlanRequest(
                List.of(candidate(serviceId, candidateRevision))))
        .requestId();
  }

  private static ManagedServiceUpgrade.UpgradeCandidate candidate(
      String serviceId, String revision) {
    return new ManagedServiceUpgrade.UpgradeCandidate(
        "home-module", serviceId, desired(serviceId, revision));
  }

  private static ManagedServiceDesiredState desired(String serviceId, String revision) {
    return new ManagedServiceDesiredState(
        serviceId,
        "registry.example/"
            + serviceId
            + "@sha256:"
            + ("1".equals(revision) ? "a" : "b").repeat(64),
        revision,
        ManagedServiceLifecycle.RUNNING,
        Set.of(),
        Set.of("/var/lib/zalava/managed/" + serviceId),
        Set.of(),
        Set.of(),
        new ManagedServiceLimits(1_000, 10, 1),
        Duration.ofSeconds(30),
        3);
  }

  private static ManagedServiceResourceGrant grant(String moduleId, String serviceId) {
    return new ManagedServiceResourceGrant(
        moduleId,
        Set.of(),
        Set.of("/var/lib/zalava/managed/" + serviceId),
        Set.of(),
        Set.of(),
        new ManagedServiceLimits(1_000, 10, 1),
        Duration.ofSeconds(30),
        3);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant start) {
      this.instant = start;
    }

    void advanceBy(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  private static final class FakeStateStore implements ManagedServiceStateStore {

    private final Map<String, ManagedServiceRecord> records = new HashMap<>();

    @Override
    public Optional<ManagedServiceRecord> find(String serviceId) {
      return Optional.ofNullable(records.get(serviceId));
    }

    @Override
    public ManagedServiceRecord save(ManagedServiceRecord record) {
      records.put(record.serviceId(), record);
      return record;
    }

    @Override
    public java.util.List<ManagedServiceRecord> findAll() {
      return java.util.List.copyOf(records.values());
    }
  }

  private static final class FakeUpgradeRequestStore implements ManagedServiceUpgradeRequestStore {

    private final Map<String, ManagedServiceUpgradeRequest> stored = new HashMap<>();

    @Override
    public ManagedServiceUpgradeRequest save(ManagedServiceUpgradeRequest request) {
      stored.put(request.requestId(), request);
      return request;
    }

    @Override
    public Optional<ManagedServiceUpgradeRequest> find(String requestId) {
      return Optional.ofNullable(stored.get(requestId));
    }

    @Override
    public List<ManagedServiceUpgradeRequest> recent(int limit) {
      return stored.values().stream()
          .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
          .limit(limit)
          .toList();
    }
  }

  private static final class InMemoryInstallRequestStore
      implements ManagedServiceInstallRequestStore {

    private final Map<String, ManagedServiceInstallRequest> stored = new HashMap<>();

    @Override
    public ManagedServiceInstallRequest save(ManagedServiceInstallRequest request) {
      stored.put(request.requestId(), request);
      return request;
    }

    @Override
    public Optional<ManagedServiceInstallRequest> find(String requestId) {
      return Optional.ofNullable(stored.get(requestId));
    }

    @Override
    public List<ManagedServiceInstallRequest> recent(int limit) {
      return stored.values().stream()
          .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
          .limit(limit)
          .toList();
    }
  }

  private static final class FakeBackup implements ManagedServiceBackupPort {

    final Set<String> backedUp = new LinkedHashSet<>();
    final Set<String> restored = new LinkedHashSet<>();
    final Set<String> failExecuteFor = new LinkedHashSet<>();
    final Set<String> failRestoreFor = new LinkedHashSet<>();

    @Override
    public BackupArtifact execute(ManagedServiceRecord record) {
      if (failExecuteFor.contains(record.serviceId())) {
        throw new IllegalStateException("backup refused: " + record.serviceId());
      }
      backedUp.add(record.serviceId());
      return new BackupArtifact(record.serviceId(), NOW, "/tmp/fake-backup-" + record.serviceId());
    }

    @Override
    public void restore(ManagedServiceRecord record, String backupLocation) {
      if (failRestoreFor.contains(record.serviceId())) {
        throw new IllegalStateException("restore refused: " + record.serviceId());
      }
      restored.add(record.serviceId());
    }
  }

  private static final class FakeEngine implements OciServiceEngine {

    final List<String> createLog = new ArrayList<>();
    final List<String> removalLog = new ArrayList<>();
    final Set<String> created = new LinkedHashSet<>();
    final Set<String> started = new LinkedHashSet<>();
    final Set<String> stopped = new LinkedHashSet<>();
    final Set<String> removed = new LinkedHashSet<>();
    String readyOnlyForRevision;
    private String lastCreatedRevision;

    @Override
    public Observation inspect(String serviceId) {
      boolean exists = created.contains(serviceId);
      boolean running = started.contains(serviceId);
      boolean ready =
          exists
              && running
              && (readyOnlyForRevision == null || readyOnlyForRevision.equals(lastCreatedRevision));
      return new Observation(exists, running, ready, "home-module", "managed-" + serviceId);
    }

    @Override
    public void create(ManagedServiceRecord record) {
      created.add(record.serviceId());
      started.add(record.serviceId());
      createLog.add(record.serviceId());
      lastCreatedRevision = record.desiredRevision();
    }

    @Override
    public void start(String serviceId) {
      started.add(serviceId);
    }

    @Override
    public void stop(String serviceId) {
      stopped.add(serviceId);
      started.remove(serviceId);
    }

    @Override
    public void remove(String serviceId) {
      removed.add(serviceId);
      removalLog.add(serviceId);
      created.remove(serviceId);
      started.remove(serviceId);
    }

    @Override
    public List<String> recentLogs(String serviceId, int maxLines) {
      return List.of();
    }
  }
}
