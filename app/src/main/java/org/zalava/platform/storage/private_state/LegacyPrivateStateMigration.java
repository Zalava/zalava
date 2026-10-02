package org.zalava.platform.storage.private_state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;

/** Migrates the legacy singleton private-state directories into one bootstrap actor partition. */
public final class LegacyPrivateStateMigration {
  private static final List<String> PRIVATE_AREAS =
      List.of("conversations", "tasks", "memory", "agent-runs", "approval-requests");

  private final Path workspace;
  private final ActorScopedPaths paths;
  private final LegacyTaskMigrationManifest taskManifest;

  public LegacyPrivateStateMigration(Path workspace, ActorScopedPaths paths) {
    this.workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
    this.paths = Objects.requireNonNull(paths, "paths");
    this.taskManifest = new LegacyTaskMigrationManifest(this.workspace);
  }

  public MigrationResult migrate(Actor bootstrapActor, boolean dryRun) {
    Objects.requireNonNull(bootstrapActor, "bootstrapActor");
    Path marker = actorRoot(bootstrapActor).resolve(".legacy-state-migration-complete");
    if (Files.exists(marker)) {
      return MigrationResult.completed();
    }
    List<Path> sources =
        PRIVATE_AREAS.stream().map(workspace::resolve).filter(Files::exists).toList();
    if (dryRun) {
      return new MigrationResult(
          false, true, sources.stream().map(Path::getFileName).map(Path::toString).toList());
    }

    Path backup =
        workspace.resolve("migration-backups").resolve(bootstrapActor.accountId().toString());
    try {
      Files.createDirectories(backup);
      for (Path source : sources) {
        copyTree(source, backup.resolve(source.getFileName()));
        if (source.getFileName().toString().equals("tasks")) {
          migrateTasks(source, bootstrapActor);
        } else {
          copyTree(source, paths.directory(bootstrapActor, source.getFileName().toString()));
        }
        deleteTree(source);
      }
      Files.writeString(marker, "complete\n");
      return new MigrationResult(
          false, false, sources.stream().map(Path::getFileName).map(Path::toString).toList());
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to migrate legacy private state", ex);
    }
  }

  public void rollback(Actor bootstrapActor) {
    Path backup =
        workspace.resolve("migration-backups").resolve(bootstrapActor.accountId().toString());
    if (!Files.isDirectory(backup)) {
      throw new IllegalStateException("No legacy private-state migration backup exists");
    }
    try {
      for (String area : PRIVATE_AREAS) {
        Path source = backup.resolve(area);
        if (Files.exists(source)) {
          copyTree(source, workspace.resolve(area));
        }
      }
      Path migratedRoot = actorRoot(bootstrapActor);
      if (Files.exists(migratedRoot)) {
        deleteTree(migratedRoot);
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to roll back legacy private-state migration", ex);
    }
  }

  private static void copyTree(Path source, Path target) throws IOException {
    try (Stream<Path> entries = Files.walk(source)) {
      for (Path entry : entries.toList()) {
        Path destination = target.resolve(source.relativize(entry));
        if (Files.isSymbolicLink(entry)) {
          throw new IllegalStateException(
              "Legacy private state contains a symbolic link: " + entry);
        }
        if (Files.isDirectory(entry)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(
              entry,
              destination,
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }

  private void migrateTasks(Path source, Actor bootstrapActor) throws IOException {
    try (Stream<Path> entries = Files.walk(source, 2)) {
      for (Path task :
          entries
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".md"))
              .toList()) {
        ActorTaskReference reference = ActorTaskReference.newReference();
        Path target = paths.file(bootstrapActor, "tasks", reference.value() + ".yaml");
        Files.copy(
            task, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        taskManifest.record(
            task.toAbsolutePath().normalize().toString(), bootstrapActor, reference);
      }
    }
  }

  private Path actorRoot(Actor actor) {
    return workspace.resolve("users").resolve(actor.accountId().toString()).normalize();
  }

  private static void deleteTree(Path directory) throws IOException {
    try (Stream<Path> entries = Files.walk(directory)) {
      for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(entry);
      }
    }
  }

  public record MigrationResult(boolean alreadyComplete, boolean dryRun, List<String> areas) {
    static MigrationResult completed() {
      return new MigrationResult(true, false, List.of());
    }
  }
}
