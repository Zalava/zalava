package org.zalava.modules.managedservices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.zalava.modules.managedservices.application.port.in.ManagedServiceInstallation;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

class DefaultManagedServiceInstallationTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  private final FakeEngine engine = new FakeEngine();
  private final FakeStateStore states = new FakeStateStore();
  private final FakeRequestStore requests = new FakeRequestStore();
  private final MutableClock clock = new MutableClock(NOW);
  private final ManagedServiceReconciler reconciler =
      new ManagedServiceReconciler(states, engine, clock);
  private final DefaultManagedServiceInstallation installation =
      new DefaultManagedServiceInstallation(
          new ManagedServiceInstallPlanning(), requests, states, reconciler, clock);

  @Test
  void executesApprovedInstallInDependencyOrder() {
    String requestId = plan("database", "app");

    var result = installation.allow(requestId);

    assertThat(result.status()).isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED);
    assertThat(states.find("database"))
        .hasValueSatisfying(
            r -> assertThat(r.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING));
    assertThat(states.find("app"))
        .hasValueSatisfying(
            r -> assertThat(r.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING));
    assertThat(engine.created).containsExactly("database", "app");
    assertThat(engine.started).containsExactly("database", "app");
  }

  @Test
  void denialRecordsDecisionWithoutStartingAnything() {
    String requestId = plan("database", "app");

    var denied = installation.deny(requestId);

    assertThat(denied.status()).isEqualTo(ManagedServiceInstallRequest.Status.DENIED);
    assertThat(denied.message()).contains("no service was started");
    assertThat(engine.created).isEmpty();
    assertThat(states.find("database")).isEmpty();
    assertThatThrownBy(() -> installation.allow(requestId))
        .isInstanceOf(ManagedServiceInstallException.class)
        .hasMessageContaining("was denied");
  }

  @Test
  void stopsAtFirstFailureAndNeverStartsLaterServices() {
    engine.failCreateFor.add("app");
    String requestId = plan("database", "app", "worker");

    var result = installation.allow(requestId);

    assertThat(result.status()).isEqualTo(ManagedServiceInstallRequest.Status.PARTIALLY_FAILED);
    assertThat(result.message())
        .contains("Stopped at 'app'")
        .contains("later services were not started");
    assertThat(states.find("database"))
        .hasValueSatisfying(
            r -> assertThat(r.observedState()).isEqualTo(ManagedServiceObservedState.RUNNING));
    assertThat(states.find("app"))
        .hasValueSatisfying(
            r -> assertThat(r.observedState()).isEqualTo(ManagedServiceObservedState.FAILED));
    assertThat(states.find("worker")).isEmpty();
    assertThat(engine.started).containsExactly("database");
  }

  @Test
  void retryAfterPartialFailureSkipsCompletedServicesAndSucceeds() {
    engine.failCreateFor.add("app");
    String requestId = plan("database", "app", "worker");
    installation.allow(requestId);
    engine.failCreateFor.clear();
    clock.advanceBy(Duration.ofSeconds(31));
    int databaseCreates = engine.createCount("database");

    var retry = installation.allow(requestId);

    assertThat(retry.status()).isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED);
    assertThat(engine.createCount("database")).isEqualTo(databaseCreates);
    assertThat(engine.created).containsExactly("database", "app", "worker");
  }

  @Test
  void allowAfterSuccessIsIdempotent() {
    String requestId = plan("database");
    installation.allow(requestId);
    int databaseCreates = engine.createCount("database");

    var again = installation.allow(requestId);

    assertThat(again.status()).isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED);
    assertThat(engine.createCount("database")).isEqualTo(databaseCreates);
    assertThat(engine.created).containsExactly("database");
  }

  @Test
  void retryHonorsReconcilerBackoffBeforeTouchingTheEngine() {
    engine.failCreateFor.add("app");
    String requestId = plan("app");
    installation.allow(requestId);
    engine.failCreateFor.clear();
    int appCreates = engine.createCount("app");

    var retry = installation.allow(requestId);

    assertThat(retry.status()).isEqualTo(ManagedServiceInstallRequest.Status.FAILED);
    assertThat(retry.message()).contains("retry honors reconciler backoff");
    assertThat(engine.createCount("app")).isEqualTo(appCreates);
  }

  @Test
  void persistsFailureAndStopsWhenTheEngineRefusesCreate() {
    engine.failAllCreates = true;
    String requestId = plan("database", "app");

    var result = installation.allow(requestId);

    assertThat(result.status()).isEqualTo(ManagedServiceInstallRequest.Status.FAILED);
    assertThat(states.find("app")).isEmpty();
    assertThat(engine.started).isEmpty();
  }

  @Test
  void rejectsPlanningWhenServiceIsAlreadyInstalledWithDifferentRevision() {
    String requestId = plan("database");
    installation.allow(requestId);
    var upgraded =
        new ManagedServiceInstallation.PlannedRequest(
            "home-module",
            "database",
            desired("database", "2"),
            grant("home-module", "database"),
            Set.of());

    assertThatThrownBy(
            () -> installation.plan(new ManagedServiceInstallation.PlanRequest(List.of(upgraded))))
        .isInstanceOf(ManagedServiceInstallException.class)
        .hasMessageContaining("already installed with a different revision or grant");
    assertThat(requests.find(requestId))
        .hasValueSatisfying(
            r -> assertThat(r.status()).isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED));
  }

  @Test
  void rejectsPendingOverlapForTheSameService() {
    plan("database");

    assertThatThrownBy(() -> plan("database"))
        .isInstanceOf(ManagedServiceInstallException.class)
        .hasMessageContaining("already has a pending install request");
  }

  private String plan(String... serviceIdsInDependencyOrder) {
    List<ManagedServiceInstallation.PlannedRequest> planned = new ArrayList<>();
    Set<String> alreadyPlanned = new LinkedHashSet<>();
    for (String serviceId : serviceIdsInDependencyOrder) {
      planned.add(
          new ManagedServiceInstallation.PlannedRequest(
              "home-module",
              serviceId,
              desired(serviceId, "1"),
              grant("home-module", serviceId),
              Set.copyOf(alreadyPlanned)));
      alreadyPlanned.add(serviceId);
    }
    return installation.plan(new ManagedServiceInstallation.PlanRequest(planned)).requestId();
  }

  private static ManagedServiceDesiredState desired(String serviceId, String revision) {
    return new ManagedServiceDesiredState(
        serviceId,
        "registry.example/" + serviceId + "@sha256:" + "a".repeat(64),
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

  private static final class FakeRequestStore implements ManagedServiceInstallRequestStore {
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

  private static final class FakeEngine implements OciServiceEngine {
    final Set<String> created = new LinkedHashSet<>();
    final Set<String> started = new LinkedHashSet<>();
    final Set<String> stopped = new LinkedHashSet<>();
    final Set<String> removed = new LinkedHashSet<>();
    final Set<String> failCreateFor = new HashSet<>();
    final Map<String, String> owners = new HashMap<>();
    boolean failAllCreates;

    int createCount(String serviceId) {
      return created.stream().filter(serviceId::equals).toList().size();
    }

    @Override
    public Observation inspect(String serviceId) {
      boolean exists = owners.containsKey(serviceId);
      return new Observation(
          exists,
          started.contains(serviceId),
          exists && started.contains(serviceId),
          owners.get(serviceId),
          exists ? "managed-" + serviceId : null);
    }

    @Override
    public void create(ManagedServiceRecord record) {
      if (failAllCreates || failCreateFor.contains(record.serviceId())) {
        throw new IllegalStateException("engine create refused: " + record.serviceId());
      }
      if (owners.containsKey(record.serviceId())) {
        throw new IllegalStateException("already created: " + record.serviceId());
      }
      created.add(record.serviceId());
      owners.put(record.serviceId(), record.grant().moduleId());
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
      owners.remove(serviceId);
      started.remove(serviceId);
    }

    @Override
    public List<String> recentLogs(String serviceId, int maxLines) {
      return List.of();
    }
  }
}
