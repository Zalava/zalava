package org.zalava.managed.adapter.out.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.SeaServiceDescriptor;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceEngine;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.ManagedServiceObservedState;
import org.zalava.managed.application.ManagedServiceRecord;
import org.zalava.managed.application.port.out.OciServiceEngine;
import org.zalava.runtime.application.port.in.RuntimeQueries;

class ModuleManagedServiceEngineTest {
  private static final String SERVICE_ID = "home-service";
  private static final String DATA_IDENTITY = "data-home-1";

  private final RuntimeQueries runtime = mock(RuntimeQueries.class);
  private final ManagedServiceEngine engine = mock(ManagedServiceEngine.class);
  private final ModuleManagedServiceEngine adapter = new ModuleManagedServiceEngine(runtime);

  @Test
  void reportsAvailabilityFromTheResolvedModuleService() {
    when(runtime.findService(ManagedServiceEngine.CONTRACT))
        .thenReturn(Optional.empty())
        .thenReturn(loaded());

    assertThat(adapter.isEngineAvailable()).isFalse();
    assertThat(adapter.isEngineAvailable()).isTrue();
  }

  @Test
  void everyOperationFailsClosedWithoutAModuleProvidedEngine() {
    when(runtime.findService(ManagedServiceEngine.CONTRACT)).thenReturn(Optional.empty());

    assertThatIllegalStateException()
        .isThrownBy(() -> adapter.inspect(SERVICE_ID))
        .withMessageContaining("managed-service-engine");
    assertThatIllegalStateException().isThrownBy(() -> adapter.create(record()));
    assertThatIllegalStateException().isThrownBy(() -> adapter.start(SERVICE_ID));
    assertThatIllegalStateException().isThrownBy(() -> adapter.stop(SERVICE_ID));
    assertThatIllegalStateException().isThrownBy(() -> adapter.remove(SERVICE_ID));
    assertThatIllegalStateException().isThrownBy(() -> adapter.recentLogs(SERVICE_ID, 10));
    verifyNoInteractions(engine);
  }

  @Test
  void inspectForwardsTheModuleObservation() {
    when(runtime.findService(ManagedServiceEngine.CONTRACT)).thenReturn(loaded());
    when(engine.inspect(SERVICE_ID))
        .thenReturn(new ManagedServiceEngine.Observation(true, true, false, "home-module", null));

    OciServiceEngine.Observation observation = adapter.inspect(SERVICE_ID);

    assertThat(observation.exists()).isTrue();
    assertThat(observation.running()).isTrue();
    assertThat(observation.ready()).isFalse();
    assertThat(observation.ownerModuleId()).isEqualTo("home-module");
    assertThat(observation.dataIdentity()).isNull();
  }

  @Test
  void createForwardsTheValidatedRecordAsARequest() {
    when(runtime.findService(ManagedServiceEngine.CONTRACT)).thenReturn(loaded());

    adapter.create(record());

    verify(engine)
        .create(new ManagedServiceEngine.Request(SERVICE_ID, desired(), grant(), DATA_IDENTITY));
  }

  @Test
  void lifecycleAndLogQueriesForwardToTheModuleEngine() {
    when(runtime.findService(ManagedServiceEngine.CONTRACT)).thenReturn(loaded());
    when(engine.recentLogs(SERVICE_ID, 10)).thenReturn(List.of("one"));

    adapter.start(SERVICE_ID);
    adapter.stop(SERVICE_ID);
    adapter.remove(SERVICE_ID);

    assertThat(adapter.recentLogs(SERVICE_ID, 10)).containsExactly("one");
    verify(engine).start(SERVICE_ID);
    verify(engine).stop(SERVICE_ID);
    verify(engine).remove(SERVICE_ID);
  }

  private Optional<RuntimeQueries.LoadedSeaService<ManagedServiceEngine>> loaded() {
    return Optional.of(
        new RuntimeQueries.LoadedSeaService<>(
            new SeaServiceDescriptor(
                ManagedServiceEngine.CONTRACT.serviceId(),
                "zalava-module-docker",
                ManagedServiceEngine.CONTRACT.contractVersion()),
            engine));
  }

  private static ManagedServiceRecord record() {
    return new ManagedServiceRecord(
        SERVICE_ID,
        desired(),
        "desired-1",
        grant(),
        "grant-1",
        ManagedServiceObservedState.ABSENT,
        null,
        DATA_IDENTITY,
        0,
        null);
  }

  private static ManagedServiceDesiredState desired() {
    return new ManagedServiceDesiredState(
        SERVICE_ID,
        "registry.example/home@sha256:" + "a".repeat(64),
        "1",
        ManagedServiceLifecycle.RUNNING,
        Set.of(),
        Set.of("/var/lib/sea/managed/home"),
        Set.of(8123),
        Set.of(),
        new ManagedServiceLimits(1_000, 2_000, 10),
        Duration.ofSeconds(30),
        3);
  }

  private static ManagedServiceResourceGrant grant() {
    return new ManagedServiceResourceGrant(
        "home-module",
        Set.of(),
        Set.of("/var/lib/sea/managed/home"),
        Set.of(8123),
        Set.of(),
        new ManagedServiceLimits(1_000, 2_000, 10),
        Duration.ofSeconds(30),
        3);
  }
}
