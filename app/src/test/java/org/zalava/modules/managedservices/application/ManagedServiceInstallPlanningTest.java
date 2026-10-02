package org.zalava.modules.managedservices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;

class ManagedServiceInstallPlanningTest {

  private final AtomicInteger revisions = new AtomicInteger();
  private final ManagedServiceInstallPlanning planning =
      new ManagedServiceInstallPlanning(() -> String.valueOf(revisions.incrementAndGet()));

  @Test
  void ordersIndependentServicesDeterministicallyByName() {
    var plan =
        planning.plan(
            List.of(
                request("zeta-service", "home-module", Set.of()),
                request("alpha-service", "home-module", Set.of())));

    assertThat(plan.services())
        .extracting(ManagedServiceInstallPlanning.PlannedService::serviceId)
        .containsExactly("alpha-service", "zeta-service");
  }

  @Test
  void ordersDependenciesBeforeDependents() {
    var plan =
        planning.plan(
            List.of(
                request("database", "home-module", Set.of()),
                request("app", "home-module", Set.of("database")),
                request("worker", "home-module", Set.of("database", "app"))));

    assertThat(plan.services())
        .extracting(ManagedServiceInstallPlanning.PlannedService::serviceId)
        .containsExactly("database", "app", "worker");
  }

  @Test
  void assignsDistinctGrantRevisionsPerService() {
    var plan =
        planning.plan(
            List.of(
                request("database", "home-module", Set.of()),
                request("app", "home-module", Set.of("database"))));

    assertThat(plan.services())
        .extracting(ManagedServiceInstallPlanning.PlannedService::grantRevision)
        .doesNotContainNull()
        .doesNotHaveDuplicates();
  }

  @Test
  void rejectsUnknownDependency() {
    assertThatThrownBy(
            () -> planning.plan(List.of(request("app", "home-module", Set.of("missing-database")))))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("Unknown managed-service dependency 'missing-database'");
  }

  @Test
  void rejectsSelfDependencyAndCycles() {
    assertThatThrownBy(() -> planning.plan(List.of(request("app", "home-module", Set.of("app")))))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("must not depend on itself");

    assertThatThrownBy(
            () ->
                planning.plan(
                    List.of(
                        request("app", "home-module", Set.of("database")),
                        request("database", "home-module", Set.of("app")))))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("dependency cycle");
  }

  @Test
  void rejectsDuplicateServiceIdsAndEmptyPlans() {
    assertThatThrownBy(
            () ->
                planning.plan(
                    List.of(
                        request("app", "home-module", Set.of()),
                        request("app", "other-module", Set.of()))))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("Duplicate managed service id");

    assertThatThrownBy(() -> planning.plan(List.of()))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("at least one service");
  }

  @Test
  void rejectsOwnerSpoofingAndGrantViolations() {
    var spoofed =
        new ManagedServiceInstallPlanning.Request(
            authority("other-module"),
            "app",
            desired(
                "app",
                new ManagedServiceLimits(1_000, 10, 1),
                Set.of(),
                Set.of(),
                Set.of("/var/lib/sea/managed/app"),
                Set.of()),
            grant("home-module", Set.of(), Set.of(), Set.of("/var/lib/sea/managed/app"), Set.of()),
            Set.of());
    assertThatThrownBy(() -> planning.plan(List.of(spoofed)))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("belongs to module 'home-module'");

    var overLimit =
        new ManagedServiceInstallPlanning.Request(
            authority("home-module"),
            "app",
            desired(
                "app",
                new ManagedServiceLimits(5_000, 10, 1),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of()),
            grant("home-module", Set.of(), Set.of(), Set.of(), Set.of()),
            Set.of());
    assertThatThrownBy(() -> planning.plan(List.of(overLimit)))
        .isInstanceOf(ManagedServiceInstallPlanningException.class)
        .hasMessageContaining("exceeds its grant");
  }

  @Test
  void aggregatesTheExactUnionOfRequestedResources() {
    var database =
        new ManagedServiceInstallPlanning.Request(
            authority("home-module"),
            "database",
            desired(
                "database",
                new ManagedServiceLimits(1_000, 10, 1),
                Set.of(5432),
                Set.of("database-password"),
                Set.of("/var/lib/sea/managed/db"),
                Set.of()),
            grant(
                "home-module",
                Set.of(5432),
                Set.of("database-password"),
                Set.of("/var/lib/sea/managed/db"),
                Set.of()),
            Set.of());
    var app =
        new ManagedServiceInstallPlanning.Request(
            authority("home-module"),
            "app",
            desired(
                "app",
                new ManagedServiceLimits(1_000, 10, 1),
                Set.of(8080, 5432),
                Set.of("database-password", "api-token"),
                Set.of("/var/lib/sea/managed/app"),
                Set.of("/dev/dri/renderD128")),
            grant(
                "home-module",
                Set.of(8080, 5432),
                Set.of("database-password", "api-token"),
                Set.of("/var/lib/sea/managed/app"),
                Set.of("/dev/dri/renderD128")),
            Set.of("database"));

    var aggregate = planning.plan(List.of(database, app)).aggregate();
    assertThat(aggregate.ports()).containsExactlyInAnyOrder(5432, 8080);
    assertThat(aggregate.secretReferences())
        .containsExactlyInAnyOrder("api-token", "database-password");
    assertThat(aggregate.dataPaths())
        .containsExactlyInAnyOrder("/var/lib/sea/managed/app", "/var/lib/sea/managed/db");
    assertThat(aggregate.devices()).containsExactly("/dev/dri/renderD128");
    assertThat(aggregate.totalLimits().cpuMillis()).isEqualTo(2_000);
    assertThat(aggregate.totalLimits().memoryBytes()).isEqualTo(20);
    assertThat(aggregate.totalLimits().processLimit()).isEqualTo(2);
  }

  private static ManagedServiceInstallPlanning.Request request(
      String serviceId, String moduleId, Set<String> dependsOn) {
    return request(serviceId, moduleId, Set.of(), Set.of(), Set.of(), dependsOn);
  }

  private static ManagedServiceInstallPlanning.Request request(
      String serviceId,
      String moduleId,
      Set<Integer> ports,
      Set<String> secrets,
      Set<String> dataPaths,
      Set<String> dependsOn) {
    return new ManagedServiceInstallPlanning.Request(
        authority(moduleId),
        serviceId,
        desired(
            serviceId, new ManagedServiceLimits(1_000, 10, 1), ports, secrets, dataPaths, Set.of()),
        grant(moduleId, ports, secrets, dataPaths, Set.of()),
        dependsOn);
  }

  private static ManagedServiceDesiredState desired(
      String serviceId,
      ManagedServiceLimits limits,
      Set<Integer> ports,
      Set<String> secrets,
      Set<String> dataPaths,
      Set<String> devices) {
    return new ManagedServiceDesiredState(
        serviceId,
        "registry.example/" + serviceId + "@sha256:" + "a".repeat(64),
        "1",
        ManagedServiceLifecycle.RUNNING,
        secrets,
        dataPaths,
        ports,
        devices,
        limits,
        Duration.ofSeconds(30),
        3);
  }

  private static ManagedServiceResourceGrant grant(
      String moduleId,
      Set<Integer> ports,
      Set<String> secrets,
      Set<String> dataPaths,
      Set<String> devices) {
    return new ManagedServiceResourceGrant(
        moduleId,
        secrets,
        dataPaths,
        ports,
        devices,
        new ManagedServiceLimits(1_000, 10, 1),
        Duration.ofSeconds(30),
        3);
  }

  private static org.zalava.ManagedServiceAuthority authority(String moduleId) {
    return new ZalavaServiceFactoryContext(moduleId, Map.of(), Map.of()).managedServiceAuthority();
  }
}
