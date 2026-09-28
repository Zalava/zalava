package org.zalava.managed.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLimits;
import org.zalava.managed.ManagedServiceResourceGrant;
import org.zalava.managed.application.ManagedServiceObservedState;
import org.zalava.managed.application.ManagedServiceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemManagedServiceBackupAdapterTest {

  @TempDir Path workspace;

  private FileSystemManagedServiceBackupAdapter adapter;

  @BeforeEach
  void createAdapter() {
    adapter = new FileSystemManagedServiceBackupAdapter(workspace);
  }

  @Test
  void refusesToPretendAServiceWithoutDataPathsWasBackedUp() {
    ManagedServiceRecord record = record("stateless", Set.of());

    assertThatThrownBy(() -> adapter.execute(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares no data paths");
  }

  @Test
  void archivesAndRestoresEveryDeclaredDataPathUnderExactlyThatPath() throws IOException {
    Path first = workspace.resolve("live").resolve("first");
    Path second = workspace.resolve("live").resolve("second");
    Files.createDirectories(first);
    Files.createDirectories(second);
    Files.writeString(first.resolve("state.txt"), "first-data");
    Files.createDirectories(first.resolve("nested"));
    Files.writeString(first.resolve("nested").resolve("deep.txt"), "first-deep");
    Files.writeString(second.resolve("state.txt"), "second-data");
    ManagedServiceRecord record = record("multi", Set.of(first.toString(), second.toString()));

    var artifact = adapter.execute(record);
    var backupDirectory = Path.of(artifact.location());

    assertThat(backupDirectory).isDirectory();
    assertThat(backupDirectory.getParent().getFileName().toString())
        .isEqualTo("multi"); // managed-backups/<service-id>/<revision>-<timestamp>/
    // Destructive content change between backup and restore.
    Files.writeString(first.resolve("state.txt"), "MUTATED");
    Files.writeString(second.resolve("state.txt"), "MUTATED");

    adapter.restore(record, artifact.location());

    assertThat(Files.readString(first.resolve("state.txt"))).isEqualTo("first-data");
    assertThat(Files.readString(first.resolve("nested").resolve("deep.txt")))
        .isEqualTo("first-deep");
    assertThat(Files.readString(second.resolve("state.txt"))).isEqualTo("second-data");
  }

  @Test
  void restoreRejectsAMissingBackupDirectory() {
    ManagedServiceRecord record = record("multi", Set.of(workspace.resolve("d").toString()));

    assertThatThrownBy(() -> adapter.restore(record, workspace.resolve("missing").toString()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is missing");
  }

  private static ManagedServiceRecord record(String serviceId, Set<String> dataPaths) {
    return new ManagedServiceRecord(
        serviceId,
        desired(serviceId, dataPaths),
        "1",
        grant(serviceId, dataPaths),
        "grant-1",
        ManagedServiceObservedState.STOPPED,
        "1",
        "managed-" + serviceId,
        0,
        null);
  }

  private static ManagedServiceDesiredState desired(String serviceId, Set<String> dataPaths) {
    return new ManagedServiceDesiredState(
        serviceId,
        "registry.example/" + serviceId + "@sha256:" + "a".repeat(64),
        "1",
        ManagedServiceLifecycle.RUNNING,
        Set.of(),
        dataPaths,
        Set.of(),
        Set.of(),
        new ManagedServiceLimits(1_000, 10, 1),
        Duration.ofSeconds(30),
        3);
  }

  private static ManagedServiceResourceGrant grant(String serviceId, Set<String> dataPaths) {
    return new ManagedServiceResourceGrant(
        "home-module",
        Set.of(),
        dataPaths,
        Set.of(),
        Set.of(),
        new ManagedServiceLimits(1_000, 10, 1),
        Duration.ofSeconds(30),
        3);
  }
}
