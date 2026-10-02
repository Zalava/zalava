package org.zalava.modules.runtime.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore.DesiredState;

class FileSystemModuleLifecycleStoreTest {
  @TempDir Path workspace;

  @Test
  void newInstallsStayStoppedAndLegacyEnabledModulesMigrateToRunning() {
    FileSystemModuleLifecycleStore store = new FileSystemModuleLifecycleStore(workspace);
    store.registerNewInstall("new-module");
    store.migrateExisting(List.of("new-module", "legacy-module"));

    FileSystemModuleLifecycleStore reloaded = new FileSystemModuleLifecycleStore(workspace);
    assertThat(reloaded.desired("new-module")).contains(DesiredState.STOPPED);
    assertThat(reloaded.desired("legacy-module")).contains(DesiredState.RUNNING);
    reloaded.registerNewInstall("legacy-module");
    assertThat(reloaded.desired("legacy-module")).contains(DesiredState.RUNNING);
  }
}
