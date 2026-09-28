package org.zalava.managed.adapter.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.zalava.managed.application.ManagedServiceInstallRequest;
import org.zalava.managed.application.port.out.ManagedServiceInstallRequestStore;

/** Atomic filesystem persistence for aggregate install decisions across application restarts. */
public final class FileSystemManagedServiceInstallRequestStore
    implements ManagedServiceInstallRequestStore {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private final Path directory;

  public FileSystemManagedServiceInstallRequestStore(Path root) {
    try {
      directory = root.resolve("managed-install-requests");
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create managed-install request store", ex);
    }
  }

  @Override
  public ManagedServiceInstallRequest save(ManagedServiceInstallRequest request) {
    Path target = path(request.requestId());
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.createDirectories(directory);
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), request);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return request;
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to persist managed-install request: " + request.requestId(), ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public Optional<ManagedServiceInstallRequest> find(String requestId) {
    Path path = path(requestId);
    if (!Files.isRegularFile(path)) return Optional.empty();
    try {
      return Optional.of(JSON.readValue(path.toFile(), ManagedServiceInstallRequest.class));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read managed-install request: " + requestId, ex);
    }
  }

  @Override
  public List<ManagedServiceInstallRequest> recent(int limit) {
    if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .sorted(Comparator.comparing(ManagedServiceInstallRequest::createdAt).reversed())
          .limit(limit)
          .toList();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to list managed-install requests", ex);
    }
  }

  private ManagedServiceInstallRequest read(Path path) {
    try {
      return JSON.readValue(path.toFile(), ManagedServiceInstallRequest.class);
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to read managed-install request: " + path.getFileName(), ex);
    }
  }

  private Path path(String requestId) {
    if (requestId == null || !requestId.matches("[A-Za-z0-9-]{1,64}")) {
      throw new IllegalArgumentException("Invalid managed-install request id");
    }
    return directory.resolve(requestId + ".json");
  }
}
