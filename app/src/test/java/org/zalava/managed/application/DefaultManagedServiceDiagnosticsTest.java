package org.zalava.managed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.port.in.ManagedServiceDiagnostics;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.OciServiceEngine;

class DefaultManagedServiceDiagnosticsTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  private final FakeEngine engine = new FakeEngine();
  private final FakeStateStore states = new FakeStateStore();
  private final ManagedServiceReconciler reconciler =
      new ManagedServiceReconciler(states, engine, Clock.fixed(NOW, ZoneOffset.UTC));
  private final DefaultManagedServiceDiagnostics diagnostics =
      new DefaultManagedServiceDiagnostics(states, reconciler, engine, Clock.systemUTC());

  @Test
  void inspectReportsTheDurableState() {
    states.save(record("database", ManagedServiceObservedState.RUNNING, 2, NOW.plusSeconds(30)));

    var report = diagnostics.inspect("database");

    assertThat(report.serviceId()).isEqualTo("database");
    assertThat(report.moduleId()).isEqualTo("home-module");
    assertThat(report.observedState()).isEqualTo("RUNNING");
    assertThat(report.consecutiveFailures()).isEqualTo(2);
    assertThat(report.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
  }

  @Test
  void inspectRejectsUnknownServices() {
    assertThatThrownBy(() -> diagnostics.inspect("ghost"))
        .isInstanceOf(ManagedServiceDiagnostics.UnknownServiceException.class)
        .hasMessageContaining("Unknown managed service");
  }

  @Test
  void logsAreClampedToTheHardCapAndEmptyRequestsStayEmpty() {
    states.save(record("database", ManagedServiceObservedState.RUNNING, 0, null));

    assertThat(diagnostics.recentLogs("database", 499)).containsExactly("log-line");
    assertThat(diagnostics.recentLogs("database", 0)).isEmpty();
    assertThat(diagnostics.recentLogs("database", -5)).isEmpty();
    assertThat(engine.logLimit).isEqualTo(499);
  }

  @Test
  void restartOnAnAlreadyRunningServiceIsIdempotent() {
    states.save(record("database", ManagedServiceObservedState.RUNNING, 0, null));

    var report = diagnostics.restart("database");

    assertThat(report.observedState()).isEqualTo("RUNNING");
    assertThat(engine.starts).isZero();
  }

  @Test
  void restartReconcilesAFailedServiceAndResetsItsFailureCounters() {
    states.save(record("database", ManagedServiceObservedState.FAILED, 3, null));

    var report = diagnostics.restart("database");

    assertThat(report.observedState()).isEqualTo("RUNNING");
    assertThat(engine.starts).isEqualTo(1);
    assertThat(states.find("database"))
        .hasValueSatisfying(record -> assertThat(record.consecutiveFailures()).isZero());
  }

  @Test
  void restartRefusesAServiceThatIsDesiredStopped() {
    states.save(stoppedRecord("database"));

    assertThatThrownBy(() -> diagnostics.restart("database"))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("desired stopped");
  }

  @Test
  void restartReportsFailureWhenTheServiceIsStillNotRunning() {
    states.save(record("database", ManagedServiceObservedState.FAILED, 0, null));
    engine.readyAfterStart = false;

    assertThatThrownBy(() -> diagnostics.restart("database"))
        .isInstanceOf(ManagedServiceUpgradeException.class)
        .hasMessageContaining("still FAILED");
  }

  private static ManagedServiceRecord record(
      String serviceId, ManagedServiceObservedState observed, int failures, Instant nextAttempt) {
    ManagedServiceDesiredState desired =
        new ManagedServiceDesiredState(
            serviceId,
            "registry.example/" + serviceId + "@sha256:" + "a".repeat(64),
            "1",
            ManagedServiceLifecycle.RUNNING,
            java.util.Set.of(),
            java.util.Set.of("/var/lib/sea/managed/" + serviceId),
            java.util.Set.of(),
            java.util.Set.of(),
            new ManagedServiceLimits(1_000, 10, 1),
            Duration.ofSeconds(30),
            3);
    ManagedServiceResourceGrant grant =
        new ManagedServiceResourceGrant(
            "home-module",
            java.util.Set.of(),
            java.util.Set.of("/var/lib/sea/managed/" + serviceId),
            java.util.Set.of(),
            java.util.Set.of(),
            new ManagedServiceLimits(1_000, 10, 1),
            Duration.ofSeconds(30),
            3);
    return new ManagedServiceRecord(
        serviceId,
        desired,
        "1",
        grant,
        "grant-1",
        observed,
        "obs-1",
        "managed-" + serviceId,
        failures,
        nextAttempt);
  }

  private static ManagedServiceRecord stoppedRecord(String serviceId) {
    ManagedServiceRecord running = record(serviceId, ManagedServiceObservedState.STOPPED, 0, null);
    ManagedServiceDesiredState previous = running.desiredState();
    ManagedServiceDesiredState stopped =
        new ManagedServiceDesiredState(
            previous.resourceId(),
            previous.artifactReference(),
            previous.revision(),
            ManagedServiceLifecycle.STOPPED,
            previous.secretReferences(),
            previous.dataPaths(),
            previous.ports(),
            previous.devices(),
            previous.limits(),
            previous.readinessDeadline(),
            previous.restartLimit());
    return new ManagedServiceRecord(
        running.serviceId(),
        stopped,
        running.desiredRevision(),
        running.grant(),
        running.grantRevision(),
        running.observedState(),
        running.observedRevision(),
        running.ownedDataIdentity(),
        running.consecutiveFailures(),
        running.nextAttemptAt());
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

  private static final class FakeEngine implements OciServiceEngine {

    int starts;
    int logLimit = -1;
    boolean readyAfterStart = true;

    @Override
    public Observation inspect(String serviceId) {
      boolean exists = true;
      boolean running = starts > 0;
      return new Observation(
          exists, running, running && readyAfterStart, "home-module", "managed-" + serviceId);
    }

    @Override
    public void create(ManagedServiceRecord record) {
      // Restart reconciliation re-creates only when the engine reports the resource absent.
    }

    @Override
    public void start(String serviceId) {
      starts++;
    }

    @Override
    public void stop(String serviceId) {
      starts = 0;
    }

    @Override
    public void remove(String serviceId) {
      starts = 0;
    }

    @Override
    public List<String> recentLogs(String serviceId, int maxLines) {
      logLimit = maxLines;
      return List.of("log-line");
    }
  }
}
