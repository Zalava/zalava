package org.zalava.modules.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.zalava.api.FactorySecretAccess;
import org.zalava.api.ModuleConfigurationStatus;
import tools.jackson.databind.ObjectMapper;

/** Private host filesystem storage for candidate/active configuration and secret values. */
public final class FileSystemModuleConfigurationStore
    implements org.zalava.modules.catalog.application.port.out.ModuleConfigurations {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path root;

  public FileSystemModuleConfigurationStore(Path privateConfigurationRoot) {
    this.root = privateConfigurationRoot.resolve("module-configuration");
  }

  public synchronized void saveCandidate(
      ModuleConfigurationSnapshot snapshot, Map<String, String> secretValues) {
    save("candidate", snapshot, secretValues);
  }

  public synchronized void promoteCandidate(String moduleId) {
    Path active = root.resolve(moduleId).resolve("active");
    if (Files.exists(active)) move(moduleId, "active", "rollback");
    move(moduleId, "candidate", "active");
  }

  public synchronized void promoteCandidates() {
    if (!Files.isDirectory(root)) return;
    try (var modules = Files.list(root)) {
      modules
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .filter(moduleId -> candidate(moduleId).isPresent())
          .toList()
          .forEach(this::promoteCandidate);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to promote module configuration candidates", ex);
    }
  }

  public synchronized void rollback(String moduleId) {
    move(moduleId, "rollback", "active");
  }

  public synchronized void quarantineCandidate(String moduleId) {
    move(moduleId, "candidate", "quarantine");
  }

  public synchronized ModuleConfigurationStatus status(String moduleId) {
    if (Files.exists(root.resolve(moduleId).resolve("quarantine")))
      return ModuleConfigurationStatus.CONFIGURATION_INVALID;
    if (candidate(moduleId).isPresent()) return ModuleConfigurationStatus.RESTART_REQUIRED;
    return active(moduleId).isPresent()
        ? ModuleConfigurationStatus.ACTIVE
        : ModuleConfigurationStatus.SETUP_REQUIRED;
  }

  public synchronized void cancelCandidate(String moduleId) {
    delete(moduleId, "candidate");
  }

  public synchronized Optional<ModuleConfigurationSnapshot> active(String moduleId) {
    return read(moduleId, "active");
  }

  public synchronized Optional<ModuleConfigurationSnapshot> candidate(String moduleId) {
    return read(moduleId, "candidate");
  }

  public synchronized Map<String, ModuleConfigurationSnapshot> activeConfigurations() {
    if (!Files.isDirectory(root)) return Map.of();
    try (var modules = Files.list(root)) {
      return modules
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .map(moduleId -> active(moduleId))
          .flatMap(Optional::stream)
          .collect(
              Collectors.toUnmodifiableMap(
                  ModuleConfigurationSnapshot::moduleId, snapshot -> snapshot));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read active module configurations", ex);
    }
  }

  public synchronized FactorySecretAccess secrets(String moduleId) {
    return secrets(moduleId, "active");
  }

  public synchronized FactorySecretAccess candidateSecrets(String moduleId) {
    return secrets(moduleId, "candidate");
  }

  private void save(
      String state, ModuleConfigurationSnapshot snapshot, Map<String, String> secretValues) {
    Path directory = root.resolve(snapshot.moduleId()).resolve(state);
    write(directory.resolve("snapshot.json"), snapshot);
    write(directory.resolve("secrets.json"), secretValues);
  }

  private void move(String moduleId, String from, String to) {
    Path source = root.resolve(moduleId).resolve(from);
    if (!Files.exists(source))
      throw new IllegalStateException("No candidate configuration for " + moduleId);
    Path target = root.resolve(moduleId).resolve(to);
    try {
      Files.createDirectories(target.getParent());
      if (Files.exists(target)) deletePath(target);
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to promote module configuration", ex);
    }
  }

  private void delete(String moduleId, String state) {
    try {
      deletePath(root.resolve(moduleId).resolve(state));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to cancel module configuration", ex);
    }
  }

  private static void deletePath(Path directory) throws IOException {
    if (Files.exists(directory))
      try (var paths = Files.walk(directory)) {
        paths
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                  }
                });
      }
  }

  private Optional<ModuleConfigurationSnapshot> read(String moduleId, String state) {
    Path file = root.resolve(moduleId).resolve(state).resolve("snapshot.json");
    try {
      return Files.exists(file)
          ? Optional.of(JSON.readValue(file.toFile(), ModuleConfigurationSnapshot.class))
          : Optional.empty();
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Unable to read module configuration", ex);
    }
  }

  private FactorySecretAccess secrets(String moduleId, String state) {
    return reference -> readSecrets(moduleId, state).getOrDefault(reference, Optional.empty());
  }

  private Map<String, Optional<char[]>> readSecrets(String moduleId, String state) {
    Path file = root.resolve(moduleId).resolve(state).resolve("secrets.json");
    try {
      if (!Files.exists(file)) return Map.of();
      Map<String, String> values =
          JSON.readValue(
              file.toFile(),
              JSON.getTypeFactory().constructMapType(Map.class, String.class, String.class));
      return values.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  Map.Entry::getKey, entry -> Optional.of(entry.getValue().toCharArray())));
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Unable to read module secrets", ex);
    }
  }

  private static void write(Path file, Object value) {
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.getParent());
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to persist module configuration", ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }
}
