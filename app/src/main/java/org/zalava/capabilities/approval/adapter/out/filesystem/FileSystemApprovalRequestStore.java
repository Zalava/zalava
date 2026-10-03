package org.zalava.capabilities.approval.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Entry;
import org.zalava.capabilities.approval.application.port.out.ApprovalRequestStore;
import tools.jackson.databind.ObjectMapper;

public final class FileSystemApprovalRequestStore implements ApprovalRequestStore {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;

  public FileSystemApprovalRequestStore(Path workspace) {
    try {
      directory = workspace.resolve("approval-requests");
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create Zalava approval request store", ex);
    }
  }

  @Override
  public List<Entry> load() {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .sorted(Comparator.comparing(Entry::createdAt).reversed())
          .toList();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load Zalava approval requests", ex);
    }
  }

  @Override
  public void save(Entry entry) {
    Path target = path(entry.requestId());
    Path temporary = directory.resolve(entry.requestId() + ".json.tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), entry);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to persist Zalava tool approval request: " + entry.requestId(), ex);
    }
  }

  @Override
  public void delete(String requestId) {
    try {
      Files.deleteIfExists(path(requestId));
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to delete Zalava tool approval request: " + requestId, ex);
    }
  }

  private Entry read(Path path) {
    try {
      return JSON.readValue(path.toFile(), Entry.class);
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Unable to read Zalava tool approval request: " + path, ex);
    }
  }

  private Path path(String requestId) {
    try {
      UUID.fromString(requestId);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid Zalava approval request id", exception);
    }
    return directory.resolve(requestId + ".json");
  }
}
