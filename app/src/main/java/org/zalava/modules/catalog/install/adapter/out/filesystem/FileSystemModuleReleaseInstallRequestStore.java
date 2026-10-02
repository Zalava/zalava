package org.zalava.modules.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.ModuleReleaseInstallRequestStore;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** Durable local storage for binary-release approval requests. */
public final class FileSystemModuleReleaseInstallRequestStore
    implements ModuleReleaseInstallRequestStore {
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .rebuild()
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();
  private final Path root;

  public FileSystemModuleReleaseInstallRequestStore(Path workspace) {
    root =
        workspace
            .toAbsolutePath()
            .normalize()
            .resolve("source-module-installation/release-requests");
    try {
      Files.createDirectories(root);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Unable to prepare module release request storage", ex);
    }
  }

  @Override
  public synchronized ModuleReleaseInstallRequest create(ModuleReleaseInstallRequest request) {
    return save(request);
  }

  @Override
  public synchronized ModuleReleaseInstallRequest get(String id) {
    try {
      return JSON.readValue(path(id).toFile(), ModuleReleaseInstallRequest.class);
    } catch (SourceModuleInstallationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException(
          "Module release installation request not found: " + id, ex);
    }
  }

  @Override
  public synchronized List<ModuleReleaseInstallRequest> recent(int limit) {
    if (limit < 1 || limit > 100)
      throw new SourceModuleInstallationException(
          "Request listing limit must be between 1 and 100");
    try {
      Files.createDirectories(root);
      try (var files = Files.list(root)) {
        return files
            .filter(Files::isRegularFile)
            .map(this::read)
            .sorted(Comparator.comparing(ModuleReleaseInstallRequest::createdAt).reversed())
            .limit(limit)
            .toList();
      }
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to list module release requests", ex);
    }
  }

  @Override
  public synchronized ModuleReleaseInstallRequest save(ModuleReleaseInstallRequest request) {
    Path target = path(request.requestId());
    Path temporary = root.resolve(request.requestId() + ".tmp");
    try {
      Files.createDirectories(root);
      JSON.writeValue(temporary.toFile(), request);
      AtomicFileOperations.replace(temporary, target);
      return request;
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to persist module release request", ex);
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

  private ModuleReleaseInstallRequest read(Path path) {
    try {
      return JSON.readValue(path.toFile(), ModuleReleaseInstallRequest.class);
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException("Unable to read module release request", ex);
    }
  }
}
