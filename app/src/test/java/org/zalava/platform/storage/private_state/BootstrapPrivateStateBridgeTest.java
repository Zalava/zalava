package org.zalava.platform.storage.private_state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;

class BootstrapPrivateStateBridgeTest {
  @TempDir java.nio.file.Path workspace;

  @Test
  void resolvesOnlyMigratedLegacyPayloadsForBootstrapActor() throws Exception {
    Actor bootstrap = new Actor(AccountId.newId());
    LegacyTaskMigrationManifest manifest = new LegacyTaskMigrationManifest(workspace);
    String legacy = Files.createFile(workspace.resolve("legacy-task.md")).toString();
    ActorTaskReference task = ActorTaskReference.newReference();
    manifest.record(legacy, bootstrap, task);

    BootstrapPrivateStateBridge bridge = new BootstrapPrivateStateBridge(bootstrap, manifest);

    assertThat(bridge.resolveLegacyScheduledTask(legacy).taskReference()).isEqualTo(task);
    assertThatThrownBy(() -> bridge.resolveLegacyScheduledTask("../../tasks/evil.md"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsManifestEntriesForAnotherActor() throws Exception {
    Actor bootstrap = new Actor(AccountId.newId());
    LegacyTaskMigrationManifest manifest = new LegacyTaskMigrationManifest(workspace);
    manifest.record("old", new Actor(AccountId.newId()), ActorTaskReference.newReference());

    assertThatThrownBy(
            () ->
                new BootstrapPrivateStateBridge(bootstrap, manifest)
                    .resolveLegacyScheduledTask("old"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
