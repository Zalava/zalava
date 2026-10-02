package org.zalava.platform.storage.private_state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.zalava.identity.accounts.domain.Actor;

/** Resolves SEA-owned private-state paths without accepting a user-controlled path segment. */
public final class ActorScopedPaths {
  private final Path workspace;

  public ActorScopedPaths(Path workspace) {
    this.workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
  }

  public Path directory(Actor actor, String area) {
    validateArea(area);
    Path directory = userRoot(actor).resolve(area).normalize();
    requireWithin(userRoot(actor), directory);
    createDirectoriesWithoutSymlinks(directory);
    return directory;
  }

  public Path file(Actor actor, String area, String filename) {
    if (filename == null
        || filename.isBlank()
        || filename.contains("/")
        || filename.contains("\\")) {
      throw new IllegalArgumentException("Private-state filename must be a single path segment");
    }
    Path directory = directory(actor, area);
    Path file = directory.resolve(filename).normalize();
    requireWithin(directory, file);
    rejectExistingSymlinks(file);
    return file;
  }

  public Path userRoot(Actor actor) {
    Objects.requireNonNull(actor, "actor");
    Path root = workspace.resolve("users").resolve(actor.accountId().toString()).normalize();
    requireWithin(workspace, root);
    createDirectoriesWithoutSymlinks(root);
    return root;
  }

  private static void validateArea(String area) {
    if (area == null || !area.matches("[a-z][a-z0-9-]*")) {
      throw new IllegalArgumentException("Invalid private-state area");
    }
  }

  private static void requireWithin(Path root, Path candidate) {
    if (!candidate.startsWith(root)) {
      throw new IllegalArgumentException("Private-state path escapes its controlled root");
    }
  }

  private static void createDirectoriesWithoutSymlinks(Path directory) {
    try {
      Path current = directory.getRoot();
      for (Path segment : directory) {
        current = current.resolve(segment);
        if (Files.exists(current)) {
          if (Files.isSymbolicLink(current) || !Files.isDirectory(current)) {
            throw new IllegalStateException(
                "Private-state directory is not a real directory: " + current);
          }
        } else {
          Files.createDirectory(current);
        }
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create private-state directory", ex);
    }
  }

  private static void rejectExistingSymlinks(Path path) {
    Path current = path.getRoot();
    for (Path segment : path) {
      current = current.resolve(segment);
      if (Files.exists(current) && Files.isSymbolicLink(current)) {
        throw new IllegalStateException("Private-state path contains a symbolic link: " + current);
      }
    }
  }
}
