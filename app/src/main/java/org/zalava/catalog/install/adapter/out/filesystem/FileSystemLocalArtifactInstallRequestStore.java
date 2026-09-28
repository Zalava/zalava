package org.zalava.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.LocalArtifactInstallRequestStore;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

public final class FileSystemLocalArtifactInstallRequestStore
    implements LocalArtifactInstallRequestStore {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .rebuild()
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();
  private final Path root;

  public FileSystemLocalArtifactInstallRequestStore(Path workspace) {
    root =
        workspace.toAbsolutePath().normalize().resolve("source-module-installation/local-requests");
    try {
      Files.createDirectories(root);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Unable to prepare local artifact request storage", ex);
    }
  }

  @Override
  public synchronized LocalArtifactInstallRequest create(LocalArtifactInstallRequest request) {
    return save(request);
  }

  @Override
  public synchronized LocalArtifactInstallRequest get(String id) {
    try {
      return JSON.readValue(path(id).toFile(), LocalArtifactInstallRequest.class);
    } catch (SourceModuleInstallationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException(
          "Local artifact installation request not found: " + id, ex);
    }
  }

  @Override
  public synchronized List<LocalArtifactInstallRequest> recent(int limit) {
    if (limit < 1 || limit > 100)
      throw new SourceModuleInstallationException(
          "Request listing limit must be between 1 and 100");
    try (var files = Files.list(root)) {
      return files
          .filter(Files::isRegularFile)
          .map(this::read)
          .sorted(Comparator.comparing(LocalArtifactInstallRequest::createdAt).reversed())
          .limit(limit)
          .toList();
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to list local artifact requests", ex);
    }
  }

  @Override
  public synchronized LocalArtifactInstallRequest save(LocalArtifactInstallRequest request) {
    Path target = path(request.requestId());
    Path temporary = root.resolve(request.requestId() + ".tmp");
    try {
      JSON.writeValue(temporary.toFile(), request);
      AtomicFileOperations.replace(temporary, target);
      return request;
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to persist local artifact request", ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private Path path(String id) {
    if (id == null || !ID.matcher(id).matches())
      throw new SourceModuleInstallationException("invalid request id: " + id);
    return root.resolve(id + ".json");
  }

  private LocalArtifactInstallRequest read(Path path) {
    try {
      return JSON.readValue(path.toFile(), LocalArtifactInstallRequest.class);
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException("Unable to read local artifact request", ex);
    }
  }
}
