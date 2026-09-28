package org.zalava.private_state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyPrivateStateMigrationTest {
  @TempDir Path workspace;

  @Test
  void dryRunLeavesLegacyStateUntouched() throws Exception {
    Files.createDirectories(workspace.resolve("memory"));
    Files.writeString(workspace.resolve("memory/entry.yaml"), "private");
    Actor actor = new Actor(AccountId.newId());
    LegacyPrivateStateMigration migration = migration();

    var result = migration.migrate(actor, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.areas()).containsExactly("memory");
    assertThat(workspace.resolve("memory/entry.yaml")).exists();
    assertThat(workspace.resolve("users")).doesNotExist();
  }

  @Test
  void migratesWithBackupAndIsIdempotent() throws Exception {
    Files.createDirectories(workspace.resolve("conversations"));
    Files.writeString(workspace.resolve("conversations/chat-web.yaml"), "private chat");
    Actor actor = new Actor(AccountId.newId());
    LegacyPrivateStateMigration migration = migration();

    var first = migration.migrate(actor, false);
    var retry = migration.migrate(actor, false);

    Path userConversation =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("conversations/chat-web.yaml");
    assertThat(first.alreadyComplete()).isFalse();
    assertThat(retry.alreadyComplete()).isTrue();
    assertThat(userConversation).hasContent("private chat");
    assertThat(
            workspace
                .resolve("migration-backups")
                .resolve(actor.accountId().toString())
                .resolve("conversations/chat-web.yaml"))
        .hasContent("private chat");
    assertThat(workspace.resolve("conversations")).doesNotExist();
  }

  @Test
  void rollbackRestoresLegacyStateAndAllowsRetry() throws Exception {
    Files.createDirectories(workspace.resolve("tasks"));
    Files.writeString(workspace.resolve("tasks/task.md"), "private task");
    Actor actor = new Actor(AccountId.newId());
    LegacyPrivateStateMigration migration = migration();

    migration.migrate(actor, false);
    migration.rollback(actor);
    var retry = migration.migrate(actor, false);

    assertThat(retry.alreadyComplete()).isFalse();
    Path actorTasks =
        workspace.resolve("users").resolve(actor.accountId().toString()).resolve("tasks");
    assertThat(actorTasks).isDirectory();
    try (var migrated = Files.list(actorTasks)) {
      assertThat(migrated.map(path -> path.getFileName().toString()))
          .singleElement()
          .matches(value -> value.matches("[0-9a-f-]{36}\\.yaml"));
    }
  }

  @Test
  void rejectsLegacySymlinksRatherThanFollowingThem() throws Exception {
    Path memory = Files.createDirectories(workspace.resolve("memory"));
    Path outside = Files.createDirectories(workspace.resolve("outside"));
    Files.writeString(outside.resolve("secret.yaml"), "outside");
    Files.createSymbolicLink(memory.resolve("escaped.yaml"), outside.resolve("secret.yaml"));

    assertThatThrownBy(() -> migration().migrate(new Actor(AccountId.newId()), false))
        .isInstanceOf(IllegalStateException.class);
  }

  private LegacyPrivateStateMigration migration() {
    return new LegacyPrivateStateMigration(workspace, new ActorScopedPaths(workspace));
  }
}
