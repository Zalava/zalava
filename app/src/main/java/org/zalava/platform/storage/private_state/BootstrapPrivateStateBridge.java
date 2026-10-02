package org.zalava.platform.storage.private_state;

import java.util.Objects;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;

/** Resolves no-actor compatibility calls exclusively to the bootstrap account. */
public final class BootstrapPrivateStateBridge {
  private final Actor bootstrapActor;
  private final LegacyTaskMigrationManifest tasks;

  public BootstrapPrivateStateBridge(Actor bootstrapActor, LegacyTaskMigrationManifest tasks) {
    this.bootstrapActor = Objects.requireNonNull(bootstrapActor, "bootstrapActor");
    this.tasks = Objects.requireNonNull(tasks, "tasks");
  }

  public Actor bootstrapActor() {
    return bootstrapActor;
  }

  public ActorTaskExecutionReference resolveLegacyScheduledTask(String legacyIdentifier) {
    ActorTaskExecutionReference resolved =
        tasks
            .resolve(legacyIdentifier)
            .orElseThrow(() -> new IllegalArgumentException("Unknown legacy scheduled task"));
    if (!bootstrapActor.equals(resolved.actor())) {
      throw new IllegalArgumentException(
          "Legacy scheduled task does not belong to bootstrap actor");
    }
    return resolved;
  }
}
