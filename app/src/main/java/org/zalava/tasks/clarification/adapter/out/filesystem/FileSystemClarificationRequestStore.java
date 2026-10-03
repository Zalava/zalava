package org.zalava.tasks.clarification.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.zalava.tasks.clarification.application.port.out.ClarificationStore;
import org.zalava.tasks.clarification.domain.ClarificationRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Atomic filesystem persistence for actor-owned clarification requests. It mirrors {@code
 * FileSystemApprovalRequestStore} but keeps clarification state in its own directory so approvals
 * and clarifications never share a store or a surface.
 */
public final class FileSystemClarificationRequestStore implements ClarificationStore {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;

  public FileSystemClarificationRequestStore(Path workspace) {
    try {
      directory = workspace.resolve("clarification-requests");
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create Zalava clarification request store", ex);
    }
  }

  @Override
  public List<ClarificationRequest> load() {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::read)
          .sorted(Comparator.comparing(ClarificationRequest::createdAt).reversed())
          .toList();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load Zalava clarification requests", ex);
    }
  }

  @Override
  public void save(ClarificationRequest request) {
    Path target = path(request.requestId());
    Path temporary = directory.resolve(request.requestId() + ".json.tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), request);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to persist Zalava clarification request: " + request.requestId(), ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public void delete(String requestId) {
    try {
      Files.deleteIfExists(path(requestId));
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to delete Zalava clarification request: " + requestId, ex);
    }
  }

  private ClarificationRequest read(Path path) {
    try {
      return JSON.readValue(path.toFile(), ClarificationRequest.class);
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Unable to read Zalava clarification request: " + path, ex);
    }
  }

  private Path path(String requestId) {
    try {
      UUID.fromString(requestId);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid Zalava clarification request id", exception);
    }
    return directory.resolve(requestId + ".json");
  }
}
