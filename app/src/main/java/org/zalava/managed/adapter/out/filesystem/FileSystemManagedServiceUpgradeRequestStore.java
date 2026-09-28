package org.zalava.managed.adapter.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.zalava.managed.application.ManagedServiceUpgradeRequest;
import org.zalava.managed.application.port.out.ManagedServiceUpgradeRequestStore;

/**
 * Atomic filesystem persistence for aggregate upgrade decisions and their per-service phases, so
 * interrupted upgrades recover exactly where they stopped.
 */
public final class FileSystemManagedServiceUpgradeRequestStore
    implements ManagedServiceUpgradeRequestStore {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private final Path directory;

  public FileSystemManagedServiceUpgradeRequestStore(Path root) {
    try {
      directory = root.resolve("managed-upgrade-requests");
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create managed-upgrade request store", ex);
    }
  }

  @Override
  public ManagedServiceUpgradeRequest save(ManagedServiceUpgradeRequest request) {
    Path target = path(request.requestId());
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), request);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return request;
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to persist managed-upgrade request: " + request.requestId(), ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public Optional<ManagedServiceUpgradeRequest> find(String requestId) {
    Path path = path(requestId);
    if (!Files.isRegularFile(path)) return Optional.empty();
    try {
      return Optional.of(JSON.readValue(path.toFile(), ManagedServiceUpgradeRequest.class));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read managed-upgrade request: " + requestId, ex);
    }
  }

  @Override
  public List<ManagedServiceUpgradeRequest> recent(int limit) {
    if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .sorted(Comparator.comparing(ManagedServiceUpgradeRequest::createdAt).reversed())
          .limit(limit)
          .toList();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to list managed-upgrade requests", ex);
    }
  }

  private ManagedServiceUpgradeRequest read(Path path) {
    try {
      return JSON.readValue(path.toFile(), ManagedServiceUpgradeRequest.class);
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to read managed-upgrade request: " + path.getFileName(), ex);
    }
  }

  private Path path(String requestId) {
    if (requestId == null || !requestId.matches("[A-Za-z0-9-]{1,64}")) {
      throw new IllegalArgumentException("Invalid managed-upgrade request id");
    }
    return directory.resolve(requestId + ".json");
  }
}
