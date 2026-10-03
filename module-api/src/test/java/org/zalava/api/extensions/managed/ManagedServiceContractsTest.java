package org.zalava.api.extensions.managed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.api.ZalavaServiceFactoryContext;

class ManagedServiceContractsTest {

  @Test
  void acceptsACompleteDeclarationWithinItsModuleBoundGrant() {
    ManagedServiceLifecycleResult result =
        ManagedServiceValidator.validate(contextAuthority(), desired(), grant("home-module"));

    assertThat(result).isInstanceOf(ManagedServiceLifecycleResult.Accepted.class);
    assertThat(((ManagedServiceLifecycleResult.Accepted) result).desiredState())
        .isEqualTo(desired());
    assertThat(ManagedServiceRuntime.CONTRACT.serviceId()).isEqualTo("managed-service-runtime");
  }

  @Test
  void rejectsOwnerSpoofingAndEveryRequestedResourceOutsideTheGrant() {
    ManagedServiceDesiredState widened =
        new ManagedServiceDesiredState(
            "home-service",
            digest(),
            "2",
            ManagedServiceLifecycle.RUNNING,
            Set.of("declared", "undeclared"),
            Set.of("/var/lib/zalava/managed/home", "/var/lib/zalava/managed/other"),
            Set.of(8123, 9999),
            Set.of("/dev/ttyUSB0", "/dev/ttyUSB1"),
            new ManagedServiceLimits(1001, 2_048, 11),
            Duration.ofSeconds(31),
            4);

    ManagedServiceLifecycleResult result =
        ManagedServiceValidator.validate(contextAuthority(), widened, grant("other-module"));

    assertThat(result)
        .isInstanceOfSatisfying(
            ManagedServiceLifecycleResult.Rejected.class,
            rejected ->
                assertThat(rejected.failures())
                    .containsExactly(
                        ManagedServiceValidationFailure.OWNER_MISMATCH,
                        ManagedServiceValidationFailure.UNDECLARED_SECRET,
                        ManagedServiceValidationFailure.UNGRANTED_DATA_PATH,
                        ManagedServiceValidationFailure.UNGRANTED_PORT,
                        ManagedServiceValidationFailure.UNGRANTED_DEVICE,
                        ManagedServiceValidationFailure.RESOURCE_LIMIT_EXCEEDED,
                        ManagedServiceValidationFailure.READINESS_DEADLINE_EXCEEDED,
                        ManagedServiceValidationFailure.RESTART_LIMIT_EXCEEDED));
  }

  @Test
  void rejectsMutableArtifactsUnsafeGrantsAndMalformedDeclarations() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ManagedServiceDesiredState(
                    "Home Service",
                    "registry.example/home:latest",
                    " ",
                    ManagedServiceLifecycle.RUNNING,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    new ManagedServiceLimits(1, 1, 1),
                    Duration.ofSeconds(1),
                    0));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ManagedServiceResourceGrant(
                    "home-module",
                    Set.of(),
                    Set.of("/"),
                    Set.of(),
                    Set.of(),
                    new ManagedServiceLimits(1, 1, 1),
                    Duration.ofSeconds(1),
                    0));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ManagedServiceResourceGrant(
                    "home-module",
                    Set.of(),
                    Set.of("/var/run/docker.sock"),
                    Set.of(),
                    Set.of(),
                    new ManagedServiceLimits(1, 1, 1),
                    Duration.ofSeconds(1),
                    0));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ManagedServiceResourceGrant(
                    "home-module",
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of("/var/run/docker.sock"),
                    new ManagedServiceLimits(1, 1, 1),
                    Duration.ofSeconds(1),
                    0));
  }

  private static org.zalava.api.ManagedServiceAuthority contextAuthority() {
    return new ZalavaServiceFactoryContext("home-module", Map.of(), Map.of())
        .managedServiceAuthority();
  }

  private static ManagedServiceDesiredState desired() {
    return new ManagedServiceDesiredState(
        "home-service",
        digest(),
        "1",
        ManagedServiceLifecycle.RUNNING,
        Set.of("declared"),
        Set.of("/var/lib/zalava/managed/home"),
        Set.of(8123),
        Set.of("/dev/ttyUSB0"),
        new ManagedServiceLimits(1_000, 2_000, 10),
        Duration.ofSeconds(30),
        3);
  }

  private static ManagedServiceResourceGrant grant(String moduleId) {
    return new ManagedServiceResourceGrant(
        moduleId,
        Set.of("declared"),
        Set.of("/var/lib/zalava/managed/home"),
        Set.of(8123),
        Set.of("/dev/ttyUSB0"),
        new ManagedServiceLimits(1_000, 2_000, 10),
        Duration.ofSeconds(30),
        3);
  }

  private static String digest() {
    return "registry.example/home@sha256:" + "a".repeat(64);
  }
}
