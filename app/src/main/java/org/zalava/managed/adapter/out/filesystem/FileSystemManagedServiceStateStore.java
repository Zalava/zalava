package org.zalava.managed.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.zalava.managed.application.ManagedServiceRecord;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import tools.jackson.databind.ObjectMapper;

/**
 * Atomic filesystem persistence for state needed to recover reconciliation after an application
 * restart.
 */
public final class FileSystemManagedServiceStateStore implements ManagedServiceStateStore {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;

  public FileSystemManagedServiceStateStore(Path root) {
    try {
      directory = root.resolve("managed-services");
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create managed-service state store", ex);
    }
  }

  @Override
  public Optional<ManagedServiceRecord> find(String serviceId) {
    Path path = path(serviceId);
    if (!Files.isRegularFile(path)) return Optional.empty();
    return read(path);
  }

  @Override
  public List<ManagedServiceRecord> findAll() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (var paths = Files.list(directory)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .flatMap(Optional::stream)
          .sorted(Comparator.comparing(ManagedServiceRecord::serviceId))
          .toList();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to list managed-service state", ex);
    }
  }

  private Optional<ManagedServiceRecord> read(Path path) {
    try {
      return Optional.of(JSON.readValue(path.toFile(), ManagedServiceRecord.class));
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "Unable to read managed-service state: " + path.getFileName(), ex);
    }
  }

  @Override
  public ManagedServiceRecord save(ManagedServiceRecord record) {
    Path target = path(record.serviceId());
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.createDirectories(directory);
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), record);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return record;
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to persist managed-service state: " + record.serviceId(), ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private Path path(String serviceId) {
    if (serviceId == null || !serviceId.matches("[a-z][a-z0-9-]{0,62}")) {
      throw new IllegalArgumentException("Invalid managed-service id");
    }
    return directory.resolve(serviceId + ".json");
  }
}
