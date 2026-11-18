package org.zalava.modules.managedservices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.api.extensions.managed.ManagedOcrService;
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceLifecycle;
import org.zalava.api.extensions.managed.ManagedServiceLimits;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

class ApprovedManagedOcrServiceTest {
  private static final String MODULE_ID = "zalava-module-tika";
  private static final String SERVICE_ID = ManagedOcrService.SERVICE_ID;
  private static final ManagedServiceLimits LIMITS = new ManagedServiceLimits(1000, 536870912, 64);
  private static final Duration DEADLINE = Duration.ofSeconds(30);

  private final ManagedServiceStateStore states = mock(ManagedServiceStateStore.class);
  private final OciServiceEngine engine = mock(OciServiceEngine.class);
  private final ApprovedManagedOcrService service =
      new ApprovedManagedOcrService(MODULE_ID, states, () -> engine);
  private final ManagedOcrService.Request request =
      new ManagedOcrService.Request("application/pdf", "eng+spa", new byte[] {1});

  @Test
  void forwardsToTheApprovedRunningRevision() {
    var worker =
        worker(
            MODULE_ID,
            true,
            ManagedServiceLifecycle.RUNNING,
            ManagedServiceObservedState.RUNNING,
            "revision");
    when(states.find(SERVICE_ID)).thenReturn(Optional.of(worker));
    when(engine.extractOcr(worker, request)).thenReturn(Optional.of("{\"text\":\"scan\"}"));

    assertThat(service.extract(request)).contains("{\"text\":\"scan\"}");
  }

  @Test
  void withholdsDocumentsFromUnapprovedOrUnavailableWorkers() {
    when(states.find(SERVICE_ID)).thenReturn(Optional.empty());
    assertThat(service.extract(request)).isEmpty();
    for (var worker :
        Set.of(
            worker(
                "other-module",
                true,
                ManagedServiceLifecycle.RUNNING,
                ManagedServiceObservedState.RUNNING,
                "revision"),
            worker(
                MODULE_ID,
                false,
                ManagedServiceLifecycle.RUNNING,
                ManagedServiceObservedState.RUNNING,
                "revision"),
            worker(
                MODULE_ID,
                true,
                ManagedServiceLifecycle.STOPPED,
                ManagedServiceObservedState.RUNNING,
                "revision"),
            worker(
                MODULE_ID,
                true,
                ManagedServiceLifecycle.RUNNING,
                ManagedServiceObservedState.STARTING,
                null),
            worker(
                MODULE_ID,
                true,
                ManagedServiceLifecycle.RUNNING,
                ManagedServiceObservedState.RUNNING,
                "old"))) {
      when(states.find(SERVICE_ID)).thenReturn(Optional.of(worker));
      assertThat(service.extract(request)).isEmpty();
    }
    verifyNoInteractions(engine);
  }

  private static ManagedServiceRecord worker(
      String owner,
      boolean isolated,
      ManagedServiceLifecycle lifecycle,
      ManagedServiceObservedState observed,
      String revision) {
    var desired =
        new ManagedServiceDesiredState(
            SERVICE_ID,
            "oci.example/worker@sha256:" + "a".repeat(64),
            "1",
            lifecycle,
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            LIMITS,
            DEADLINE,
            2,
            isolated);
    var grant =
        new ManagedServiceResourceGrant(
            owner, Set.of(), Set.of(), Set.of(), Set.of(), LIMITS, DEADLINE, 2);
    return new ManagedServiceRecord(
        SERVICE_ID, desired, "revision", grant, "grant", observed, revision, "data-id", 0, null);
  }
}
