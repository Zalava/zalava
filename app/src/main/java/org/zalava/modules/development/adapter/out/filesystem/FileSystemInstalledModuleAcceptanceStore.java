package org.zalava.modules.development.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.development.InstalledModuleAcceptance;
import org.zalava.modules.development.application.port.out.InstalledModuleAcceptanceStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Atomic filesystem adapter; it is intentionally separate from the enablement registry. */
public final class FileSystemInstalledModuleAcceptanceStore
    implements InstalledModuleAcceptanceStore {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<Map<String, InstalledModuleAcceptance>> TYPE =
      new TypeReference<>() {};
  private final Path file;

  public FileSystemInstalledModuleAcceptanceStore(Path workspace) {
    this.file = workspace.resolve("source-module-installation/installed-acceptance.json");
  }

  @Override
  public synchronized Optional<InstalledModuleAcceptance> find(String moduleId) {
    return Optional.ofNullable(read().get(moduleId));
  }

  @Override
  public synchronized InstalledModuleAcceptance save(InstalledModuleAcceptance value) {
    Map<String, InstalledModuleAcceptance> values = new LinkedHashMap<>(read());
    values.put(value.moduleId(), value);
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.getParent());
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), values);
      Files.move(
          temporary,
          file,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      return value;
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Unable to persist installed acceptance evidence", ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private Map<String, InstalledModuleAcceptance> read() {
    if (!Files.isRegularFile(file)) return Map.of();
    try {
      return Map.copyOf(JSON.readValue(file.toFile(), TYPE));
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException(
          "Unable to read installed acceptance evidence", ex);
    }
  }
}
