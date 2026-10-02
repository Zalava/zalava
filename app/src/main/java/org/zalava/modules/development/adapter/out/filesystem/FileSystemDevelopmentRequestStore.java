package org.zalava.modules.development.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.modules.development.DevelopmentRequestException;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;
import tools.jackson.databind.ObjectMapper;

@Component
public final class FileSystemDevelopmentRequestStore implements DevelopmentRequestStore {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path storageDirectory;

  public FileSystemDevelopmentRequestStore(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    this.storageDirectory =
        workspace
            .getFilePath()
            .toAbsolutePath()
            .normalize()
            .resolve("module-development")
            .resolve("requests");
    Files.createDirectories(storageDirectory);
  }

  @Override
  public synchronized ModuleDevelopmentRequest get(DevelopmentRequestId id) {
    Path path = requestPath(id);
    if (!Files.isRegularFile(path))
      throw new DevelopmentRequestException("Development request not found: " + id.value());
    try {
      return JSON.readValue(path.toFile(), ModuleDevelopmentRequest.class);
    } catch (RuntimeException ex) {
      throw new DevelopmentRequestException(
          "Unable to read development request: " + id.value(), ex);
    }
  }

  @Override
  public synchronized ModuleDevelopmentRequest save(ModuleDevelopmentRequest request) {
    Path target = requestPath(request.id());
    Path temporary = storageDirectory.resolve(request.id().value() + ".json.tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), request);
      atomicReplace(temporary, target);
      return request;
    } catch (IOException ex) {
      throw new DevelopmentRequestException(
          "Unable to persist development request: " + request.id().value(), ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private Path requestPath(DevelopmentRequestId id) {
    if (id == null)
      throw new DevelopmentRequestException("development request id must not be null");
    return storageDirectory.resolve(id.value() + ".json");
  }

  private static void atomicReplace(Path temporary, Path target) throws IOException {
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
