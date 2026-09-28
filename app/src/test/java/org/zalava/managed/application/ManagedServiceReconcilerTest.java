package org.zalava.managed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.ManagedServiceAuthority;
import org.zalava.SeaServiceFactoryContext;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.OciServiceEngine;

class ManagedServiceReconcilerTest {
  private final FakeStore states = new FakeStore();
  private final FakeEngine engine = new FakeEngine();
  private final ManagedServiceReconciler reconciler =
      new ManagedServiceReconciler(
          states, engine, Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void createsStartsAndPersistsAReadyOwnedService() {
    engine.readyAfterStart = true;

    ManagedServiceRecord result =
        reconciler.reconcile(authority(), record(ManagedServiceLifecycle.RUNNING));

    assertThat(result.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING);
    assertThat(result.desiredRevision()).isEqualTo("desired-1");
    assertThat(result.grantRevision()).isEqualTo("grant-1");
    assertThat(result.ownedDataIdentity()).isEqualTo("data-home-1");
    assertThat(engine.creates).isEqualTo(1);
    assertThat(engine.starts).isEqualTo(1);
    assertThat(states.find(result.serviceId())).contains(result);
  }

  @Test
  void stopsExistingOwnedServiceWithoutDiscardingPersistentDataIdentity() {
    engine.exists = true;
    engine.running = true;

    ManagedServiceRecord result =
        reconciler.reconcile(authority(), record(ManagedServiceLifecycle.STOPPED));

    assertThat(result.observedState()).isEqualTo(ManagedServiceObservedState.STOPPED);
    assertThat(result.ownedDataIdentity()).isEqualTo("data-home-1");
    assertThat(engine.stops).isEqualTo(1);
  }

  @Test
  void recordsBackoffAndEventuallyCrashLoopFailureWhenReadinessNeverArrives() {
    ManagedServiceRecord first =
        reconciler.reconcile(authority(), record(ManagedServiceLifecycle.RUNNING));
    ManagedServiceRecord exhausted =
        reconciler.reconcile(
            authority(),
            new ManagedServiceRecord(
                first.serviceId(),
                first.desiredState(),
                first.desiredRevision(),
                first.grant(),
                first.grantRevision(),
                first.observedState(),
                first.observedRevision(),
                first.ownedDataIdentity(),
                3,
                null));

    assertThat(first.consecutiveFailures()).isEqualTo(1);
    assertThat(first.nextAttemptAt()).isEqualTo(Instant.parse("2026-09-09T12:00:30Z"));
    assertThat(exhausted.observedState()).isEqualTo(ManagedServiceObservedState.FAILED);
    assertThat(exhausted.consecutiveFailures()).isEqualTo(3);
  }

  @Test
  void preservesAForeignOrMismatchedOrphanInsteadOfOperatingOnIt() {
    engine.exists = true;
    engine.owner = "other-module";

    ManagedServiceRecord result =
        reconciler.reconcile(authority(), record(ManagedServiceLifecycle.RUNNING));

    assertThat(result.observedState()).isEqualTo(ManagedServiceObservedState.FAILED);
    assertThat(engine.creates + engine.starts + engine.stops).isZero();
  }

  @Test
  void rejectsAChangedGrantBeforeTheEngineIsTouched() {
    ManagedServiceRecord record = record(ManagedServiceLifecycle.RUNNING);
    ManagedServiceResourceGrant otherGrant =
        new ManagedServiceResourceGrant(
            "other-module",
            Set.of(),
            Set.of("/var/lib/sea/managed/home"),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1000, 10, 1),
            Duration.ofSeconds(30),
            3);
    ManagedServiceRecord invalid =
        new ManagedServiceRecord(
            record.serviceId(),
            record.desiredState(),
            record.desiredRevision(),
            otherGrant,
            record.grantRevision(),
            record.observedState(),
            record.observedRevision(),
            record.ownedDataIdentity(),
            0,
            null);

    assertThatThrownBy(() -> reconciler.reconcile(authority(), invalid))
        .isInstanceOf(IllegalStateException.class);
    assertThat(engine.creates + engine.starts + engine.stops).isZero();
  }

  private static ManagedServiceAuthority authority() {
    return new SeaServiceFactoryContext("home-module", Map.of(), Map.of())
        .managedServiceAuthority();
  }

  private static ManagedServiceRecord record(ManagedServiceLifecycle lifecycle) {
    var desired =
        new ManagedServiceDesiredState(
            "home-service",
            "registry.example/home@sha256:" + "a".repeat(64),
            "1",
            lifecycle,
            Set.of(),
            Set.of("/var/lib/sea/managed/home"),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1000, 10, 1),
            Duration.ofSeconds(30),
            3);
    var grant =
        new ManagedServiceResourceGrant(
            "home-module",
            Set.of(),
            Set.of("/var/lib/sea/managed/home"),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1000, 10, 1),
            Duration.ofSeconds(30),
            3);
    return new ManagedServiceRecord(
        "home-service",
        desired,
        "desired-1",
        grant,
        "grant-1",
        ManagedServiceObservedState.ABSENT,
        null,
        "data-home-1",
        0,
        null);
  }

  private static final class FakeStore implements ManagedServiceStateStore {
    private final Map<String, ManagedServiceRecord> records = new java.util.HashMap<>();

    public Optional<ManagedServiceRecord> find(String serviceId) {
      return Optional.ofNullable(records.get(serviceId));
    }

    public ManagedServiceRecord save(ManagedServiceRecord record) {
      records.put(record.serviceId(), record);
      return record;
    }

    public java.util.List<ManagedServiceRecord> findAll() {
      return java.util.List.copyOf(records.values());
    }
  }

  private static final class FakeEngine implements OciServiceEngine {
    public List<String> recentLogs(String serviceId, int maxLines) {
      return List.of();
    }

    boolean exists;
    boolean running;
    boolean readyAfterStart;
    String owner = "home-module";
    int creates;
    int starts;
    int stops;
    int removes;

    public Observation inspect(String serviceId) {
      return new Observation(exists, running, readyAfterStart && running, owner, "data-home-1");
    }

    public void create(ManagedServiceRecord record) {
      exists = true;
      creates++;
    }

    public void start(String serviceId) {
      running = true;
      starts++;
    }

    public void stop(String serviceId) {
      running = false;
      stops++;
    }

    public void remove(String serviceId) {
      exists = false;
      running = false;
      removes++;
    }
  }
}
