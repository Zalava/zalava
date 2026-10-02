package org.zalava.modules.managedservices.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest.Phase;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest.Status;

class FileSystemManagedServiceUpgradeRequestStoreTest {
  @TempDir Path root;

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  @Test
  void atomicallyPersistsAndReloadsEveryPhaseAndCapture() {
    var store = new FileSystemManagedServiceUpgradeRequestStore(root);
    var request = request();

    store.save(request);

    assertThat(new FileSystemManagedServiceUpgradeRequestStore(root).find("req-1"))
        .contains(request);
  }

  @Test
  void recentOrdersByCreationDescendingAndHonorsTheLimit() {
    var store = new FileSystemManagedServiceUpgradeRequestStore(root);
    store.save(request());
    store.save(
        new ManagedServiceUpgradeRequest(
            "req-2",
            1,
            NOW.plusSeconds(60),
            List.of(planned("database")),
            Status.PENDING,
            null,
            "Awaiting approval"));

    var recent = store.recent(1);

    assertThat(recent).hasSize(1);
    assertThat(recent.get(0).requestId()).isEqualTo("req-2");
    assertThat(store.recent(10)).hasSize(2);
  }

  @Test
  void rejectsAnUnsafeRequestIdBeforeItCanBecomeAFilePath() {
    var store = new FileSystemManagedServiceUpgradeRequestStore(root);

    assertThatIllegalArgumentException().isThrownBy(() -> store.find("../escape"));
  }

  private static ManagedServiceUpgradeRequest request() {
    return new ManagedServiceUpgradeRequest(
        "req-1",
        3,
        NOW,
        List.of(planned("database"), planned("app").withPhase(Phase.BACKED_UP).backedUp("/tmp/b")),
        Status.PROMOTING,
        null,
        "Executing approved upgrade");
  }

  private static ManagedServiceUpgradeRequest.PlannedUpgrade planned(String serviceId) {
    ManagedServiceDesiredState candidate =
        new ManagedServiceDesiredState(
            serviceId,
            "registry.example/" + serviceId + "@sha256:" + "b".repeat(64),
            "2",
            ManagedServiceLifecycle.RUNNING,
            Set.of(),
            Set.of("/var/lib/sea/managed/" + serviceId),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1_000, 10, 1),
            Duration.ofSeconds(30),
            3);
    ManagedServiceDesiredState previous =
        new ManagedServiceDesiredState(
            serviceId,
            "registry.example/" + serviceId + "@sha256:" + "a".repeat(64),
            "1",
            ManagedServiceLifecycle.RUNNING,
            Set.of(),
            Set.of("/var/lib/sea/managed/" + serviceId),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1_000, 10, 1),
            Duration.ofSeconds(30),
            3);
    return new ManagedServiceUpgradeRequest.PlannedUpgrade(
        "home-module", serviceId, candidate, previous, "managed-" + serviceId, Phase.PLANNED, null);
  }
}
