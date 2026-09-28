package org.zalava.private_state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;

/**
 * Allowlist for scheduled jobs created before private state was actor scoped.
 *
 * <p>The manifest deliberately stores legacy identifiers as data and never turns a supplied value
 * into a filesystem path. It is a temporary bridge until all scheduler callers are actor-aware.
 */
public final class LegacyTaskMigrationManifest {
  private static final String FILE_NAME = "legacy-task-manifest.tsv";

  private final Path file;
  private final Map<String, ActorTaskExecutionReference> entries = new LinkedHashMap<>();

  public LegacyTaskMigrationManifest(Path workspace) {
    this.file =
        Objects.requireNonNull(workspace, "workspace")
            .toAbsolutePath()
            .normalize()
            .resolve("migration-state")
            .resolve(FILE_NAME);
    load();
  }

  public synchronized void record(
      String legacyIdentifier, Actor actor, ActorTaskReference taskReference) {
    validateLegacyIdentifier(legacyIdentifier);
    entries.put(legacyIdentifier, new ActorTaskExecutionReference(actor, taskReference));
    persist();
  }

  public synchronized Optional<ActorTaskExecutionReference> resolve(String legacyIdentifier) {
    if (legacyIdentifier == null
        || legacyIdentifier.indexOf('\n') >= 0
        || legacyIdentifier.indexOf('\r') >= 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(entries.get(legacyIdentifier));
  }

  private void load() {
    if (!Files.isRegularFile(file)) return;
    try {
      for (String line : Files.readAllLines(file)) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 2)
          throw new IllegalStateException("Invalid legacy task migration manifest");
        validateLegacyIdentifier(parts[0]);
        entries.put(parts[0], ActorTaskExecutionReference.parse(parts[1]));
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read legacy task migration manifest", exception);
    }
  }

  private void persist() {
    try {
      Files.createDirectories(file.getParent());
      String content =
          entries.entrySet().stream()
              .map(entry -> entry.getKey() + "\t" + entry.getValue().encode())
              .reduce("", (left, right) -> left + right + "\n");
      Files.writeString(
          file,
          content,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to persist legacy task migration manifest", exception);
    }
  }

  private static void validateLegacyIdentifier(String value) {
    if (value == null
        || value.isBlank()
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0
        || value.indexOf('\t') >= 0) {
      throw new IllegalArgumentException("Invalid legacy task identifier");
    }
  }
}
