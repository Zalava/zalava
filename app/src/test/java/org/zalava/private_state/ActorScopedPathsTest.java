package org.zalava.private_state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class ActorScopedPathsTest {
  @TempDir Path workspace;

  @Test
  void derivesPrivatePathsFromOpaqueActorIdentity() {
    Actor actor = new Actor(AccountId.newId());

    Path file = new ActorScopedPaths(workspace).file(actor, "memory", "entry.yaml");

    assertThat(file)
        .isEqualTo(
            workspace
                .resolve("users")
                .resolve(actor.accountId().toString())
                .resolve("memory/entry.yaml"));
  }

  @Test
  void rejectsTraversalAndEncodedPathSegments() {
    Actor actor = new Actor(AccountId.newId());
    ActorScopedPaths paths = new ActorScopedPaths(workspace);

    assertThatThrownBy(() -> paths.file(actor, "memory", "../other.yaml"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> paths.file(actor, "memory", "nested/other.yaml"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> paths.directory(actor, "../memory"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSymlinkedActorPartitions() throws Exception {
    Actor actor = new Actor(AccountId.newId());
    Path users = Files.createDirectories(workspace.resolve("users"));
    Path outside = Files.createDirectories(workspace.resolve("outside"));
    Files.createSymbolicLink(users.resolve(actor.accountId().toString()), outside);

    assertThatThrownBy(() -> new ActorScopedPaths(workspace).directory(actor, "memory"))
        .isInstanceOf(IllegalStateException.class);
  }
}
