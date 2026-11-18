package org.zalava.modules.managedservices.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceLifecycle;
import org.zalava.api.extensions.managed.ManagedServiceLimits;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.ManagedServiceObservedState;
import org.zalava.modules.managedservices.application.ManagedServiceRecord;

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

  @Test
  void readsRecordsWrittenBeforeNetworkIsolationWasAdded() throws Exception {
    var store = new FileSystemManagedServiceStateStore(root);
    store.save(record());
    Path saved = root.resolve("managed-services/home-service.json");
    String json = Files.readString(saved).replaceAll(",\\s*\"networkIsolated\"\\s*:\\s*false", "");
    Files.writeString(saved, json);

    assertThat(store.find("home-service")).contains(record());
  }

  @Test
  void persistsNetworkIsolation() {
    var base = record();
    var state = base.desiredState();
    var isolated =
        new ManagedServiceDesiredState(
            state.resourceId(),
            state.artifactReference(),
            state.revision(),
            state.lifecycle(),
            state.secretReferences(),
            state.dataPaths(),
            state.ports(),
            state.devices(),
            state.limits(),
            state.readinessDeadline(),
            state.restartLimit(),
            true);
    var record =
        new ManagedServiceRecord(
            base.serviceId(),
            isolated,
            base.desiredRevision(),
            base.grant(),
            base.grantRevision(),
            base.observedState(),
            base.observedRevision(),
            base.ownedDataIdentity(),
            0,
            null);
    var store = new FileSystemManagedServiceStateStore(root);
    store.save(record);
    assertThat(store.find("home-service")).contains(record);
  }

  private static ManagedServiceRecord record() {
    var desired =
        new ManagedServiceDesiredState(
            "home-service",
            "registry.example/home@sha256:" + "a".repeat(64),
            "1",
            ManagedServiceLifecycle.RUNNING,
            Set.of(),
            Set.of("/var/lib/zalava/managed/home"),
            Set.of(),
            Set.of(),
            new ManagedServiceLimits(1000, 10, 1),
            Duration.ofSeconds(30),
            3);
    var grant =
        new ManagedServiceResourceGrant(
            "home-module",
            Set.of(),
            Set.of("/var/lib/zalava/managed/home"),
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
