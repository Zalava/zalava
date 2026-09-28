package org.zalava.managed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.port.in.ManagedServiceInstallation;
import org.junit.jupiter.api.Test;

class DefaultModuleManagedServiceInstallationTest {

  private static final String MODULE_ID = "sea-module-declared";

  @Test
  void approvesAndDeniesThePendingRequestForAModule() {
    FakeInstallations installations = new FakeInstallations();
    installations.requests.add(request("req-1", ManagedServiceInstallRequest.Status.PENDING));
    DefaultModuleManagedServiceInstallation subject =
        new DefaultModuleManagedServiceInstallation(ignored -> List.of(), installations);

    assertThat(subject.approveLatest(MODULE_ID).requestId()).isEqualTo("req-1");
    assertThat(installations.allowed).isEqualTo("req-1");

    assertThat(subject.denyLatest(MODULE_ID).requestId()).isEqualTo("req-1");
    assertThat(installations.denied).isEqualTo("req-1");
  }

  @Test
  void rejectsApprovalOrDenialWithoutAPendingRequest() {
    FakeInstallations installations = new FakeInstallations();
    installations.requests.add(request("req-1", ManagedServiceInstallRequest.Status.SUCCEEDED));
    DefaultModuleManagedServiceInstallation subject =
        new DefaultModuleManagedServiceInstallation(ignored -> List.of(), installations);

    assertThatIllegalArgumentException().isThrownBy(() -> subject.approveLatest(MODULE_ID));
    assertThatIllegalArgumentException().isThrownBy(() -> subject.denyLatest(MODULE_ID));
  }

  private static ManagedServiceInstallRequest request(
      String requestId, ManagedServiceInstallRequest.Status status) {
    ManagedServiceDesiredState desired = desired();
    return new ManagedServiceInstallRequest(
        requestId,
        Instant.parse("2026-09-22T00:00:00Z"),
        List.of(
            new ManagedServiceInstallPlanning.PlannedService(
                MODULE_ID,
                desired.resourceId(),
                desired,
                grant(desired),
                desired.revision(),
                "grant-1",
                Set.of())),
        new ManagedServiceInstallPlanning.AggregatedResources(
            Set.of(), Set.of(), Set.of(), Set.of(), new ManagedServiceLimits(1, 1, 1)),
        status,
        null,
        null);
  }

  private static ManagedServiceDesiredState desired() {
    return new ManagedServiceDesiredState(
        "declared-service",
        "ghcr.io/example/declared@sha256:" + "a".repeat(64),
        "1.0.0",
        ManagedServiceLifecycle.RUNNING,
        Set.of(),
        Set.of("/var/lib/sea/managed/declared"),
        Set.of(9000),
        Set.of(),
        new ManagedServiceLimits(500, 268_435_456, 64),
        Duration.ofMinutes(1),
        2);
  }

  private static ManagedServiceResourceGrant grant(ManagedServiceDesiredState desired) {
    return new ManagedServiceResourceGrant(
        MODULE_ID,
        desired.secretReferences(),
        desired.dataPaths(),
        desired.ports(),
        desired.devices(),
        desired.limits(),
        desired.readinessDeadline(),
        desired.restartLimit());
  }

  private static final class FakeInstallations implements ManagedServiceInstallation {
    private final List<ManagedServiceInstallRequest> requests = new ArrayList<>();
    private String allowed;
    private String denied;

    @Override
    public ManagedServiceInstallRequest plan(PlanRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ManagedServiceInstallRequest get(String requestId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ManagedServiceInstallRequest> recent(int limit) {
      return requests.stream().limit(limit).toList();
    }

    @Override
    public ManagedServiceInstallRequest allow(String requestId) {
      allowed = requestId;
      return requests.getFirst();
    }

    @Override
    public ManagedServiceInstallRequest deny(String requestId) {
      denied = requestId;
      return requests.getFirst();
    }
  }
}
