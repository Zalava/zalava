package org.zalava.private_state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the legacy scheduler migration manifest: persistence across instances, parse validation,
 * and the identifier allowlist. Identifiers stay data; nothing turns them into paths.
 */
class LegacyTaskMigrationManifestTest {

  @TempDir Path workspace;

  private Actor actor() {
    return new Actor(AccountId.newId());
  }

  @Test
  void recordedEntriesSurviveASecondManifestInstance() {
    Actor actor = actor();
    ActorTaskReference task = ActorTaskReference.newReference();
    var first = new LegacyTaskMigrationManifest(workspace);

    first.record("daily-check-1", actor, task);
    assertThat(first.resolve("daily-check-1"))
        .contains(new ActorTaskExecutionReference(actor, task));

    var second = new LegacyTaskMigrationManifest(workspace);
    assertThat(second.resolve("daily-check-1"))
        .contains(new ActorTaskExecutionReference(actor, task));
    assertThat(Files.isRegularFile(workspace.resolve("migration-state/legacy-task-manifest.tsv")))
        .isTrue();
  }

  @Test
  void unknownAndUnsafeIdentifiersResolveToEmpty() {
    var manifest = new LegacyTaskMigrationManifest(workspace);

    assertThat(manifest.resolve("absent")).isEmpty();
    assertThat(manifest.resolve(null)).isEmpty();
    assertThat(manifest.resolve("line-one\nline-two")).isEmpty();
    assertThat(manifest.resolve("carriage\rreturn")).isEmpty();
  }

  @Test
  void recordRejectsBlankNewlineCarriageAndTabIdentifiers() {
    var manifest = new LegacyTaskMigrationManifest(workspace);
    Actor actor = actor();
    ActorTaskReference task = ActorTaskReference.newReference();

    assertThatThrownBy(() -> manifest.record(" ", actor, task))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid legacy task identifier");
    assertThatThrownBy(() -> manifest.record(null, actor, task))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid legacy task identifier");
    assertThatThrownBy(() -> manifest.record("a\nb", actor, task))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> manifest.record("a\rb", actor, task))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> manifest.record("a\tb", actor, task))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCorruptManifestFileIsRejectedOnLoad() throws Exception {
    Path file =
        Files.createDirectories(workspace.resolve("migration-state"))
            .resolve("legacy-task-manifest.tsv");
    Files.writeString(file, "row-without-separator\n");

    assertThatThrownBy(() -> new LegacyTaskMigrationManifest(workspace))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid legacy task migration manifest");
  }

  @Test
  void reRecordingAnIdentifierReplacesTheEntry() {
    var manifest = new LegacyTaskMigrationManifest(workspace);
    Actor first = actor();
    Actor second = actor();
    ActorTaskReference task = ActorTaskReference.newReference();

    manifest.record("daily-check-1", first, task);
    manifest.record("daily-check-1", second, task);

    assertThat(manifest.resolve("daily-check-1"))
        .contains(new ActorTaskExecutionReference(second, task));
    assertThat(new LegacyTaskMigrationManifest(workspace).resolve("daily-check-1"))
        .contains(new ActorTaskExecutionReference(second, task));
  }
}
