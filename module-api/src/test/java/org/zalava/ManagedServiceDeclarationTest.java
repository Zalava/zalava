package org.zalava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;

class ManagedServiceDeclarationTest {

  @Test
  void acceptsADigestPinnedDeclarationWithNoDependenciesByDefault() {
    ManagedServiceDeclaration declaration =
        new ManagedServiceDeclaration("home-service", desired("home-service"));

    assertThat(declaration.dependsOn()).isEmpty();
    assertThat(declaration.desiredState().resourceId()).isEqualTo("home-service");
  }

  @Test
  void rejectsAMismatchedServiceIdAndSelfDependency() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ManagedServiceDeclaration("other-service", desired("home-service")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ManagedServiceDeclaration(
                    "home-service", desired("home-service"), Set.of("home-service")));
  }

  @Test
  void rejectsAnInvalidServiceIdAndMissingDesiredState() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ManagedServiceDeclaration("Home Service", desired("home-service")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ManagedServiceDeclaration("home-service", null));
  }

  private static ManagedServiceDesiredState desired(String resourceId) {
    return new ManagedServiceDesiredState(
        resourceId,
        "ghcr.io/example/home@sha256:" + "a".repeat(64),
        "1.0.0",
        ManagedServiceLifecycle.RUNNING,
        Set.of("home-token"),
        Set.of("/var/lib/sea/managed/home"),
        Set.of(8123),
        Set.of(),
        new ManagedServiceLimits(1000, 1024, 10),
        Duration.ofSeconds(30),
        3);
  }
}
