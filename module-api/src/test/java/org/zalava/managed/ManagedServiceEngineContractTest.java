package org.zalava.managed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagedServiceEngineContractTest {

  @Test
  void keepsAStableServiceIdentity() {
    assertThat(ManagedServiceEngine.CONTRACT.serviceId()).isEqualTo("managed-service-engine");
    assertThat(ManagedServiceEngine.CONTRACT.contractVersion()).isEqualTo("2");
    assertThat(ManagedServiceEngine.CONTRACT.serviceType()).isEqualTo(ManagedServiceEngine.class);
  }

  @Test
  void requestRequiresCanonicalIdentityAndCompleteState() {
    assertThatIllegalArgumentException().isThrownBy(() -> request("Home Service"));
    assertThatIllegalArgumentException().isThrownBy(() -> request(" "));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ManagedServiceEngine.Request("home-service", null, grant(), "data-home-1"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ManagedServiceEngine.Request("home-service", desired(), null, "data-home-1"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ManagedServiceEngine.Request("home-service", desired(), grant(), " "));
  }

  @Test
  void requestCarriesTheValidatedStateGrantAndOwnedIdentity() {
    ManagedServiceEngine.Request request =
        new ManagedServiceEngine.Request("home-service", desired(), grant(), "data-home-1");

    assertThat(request.serviceId()).isEqualTo("home-service");
    assertThat(request.desiredState()).isEqualTo(desired());
    assertThat(request.grant()).isEqualTo(grant());
    assertThat(request.ownedDataIdentity()).isEqualTo("data-home-1");
  }

  private static ManagedServiceEngine.Request request(String serviceId) {
    return new ManagedServiceEngine.Request(serviceId, desired(), grant(), "data-home-1");
  }

  @Test
  void contractVersionTwoExposesTheRemovalPrimitive() throws Exception {
    assertThat(ManagedServiceEngine.class.getMethod("remove", String.class))
        .isNotNull(); // Reflection check: the method must exist on the interface surface itself.
  }

  private static ManagedServiceDesiredState desired() {
    return new ManagedServiceDesiredState(
        "home-service",
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
