package org.zalava.managed.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.ManagedServiceObservedState;
import org.zalava.managed.application.ManagedServiceRecord;

class FileSystemManagedServiceStateStoreTest {
  @TempDir Path root;

  @Test
  void atomicallyPersistsAndReloadsAllIndependentRevisions() {
    var store = new FileSystemManagedServiceStateStore(root);
    var record = record();

    assertThat(store.find("home-service")).isEmpty();
    store.save(record);

    assertThat(new FileSystemManagedServiceStateStore(root).find("home-service")).contains(record);
  }

  @Test
  void rejectsAnUnsafeServiceIdBeforeItCanBecomeAFilePath() {
    var store = new FileSystemManagedServiceStateStore(root);

    assertThatIllegalArgumentException().isThrownBy(() -> store.find("../escape"));
  }

  private static ManagedServiceRecord record() {
    var desired =
        new ManagedServiceDesiredState(
            "home-service",
            "registry.example/home@sha256:" + "a".repeat(64),
            "1",
            ManagedServiceLifecycle.RUNNING,
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
        ManagedServiceObservedState.RUNNING,
        "observed-1",
        "data-home-1",
        0,
        null);
  }
}
