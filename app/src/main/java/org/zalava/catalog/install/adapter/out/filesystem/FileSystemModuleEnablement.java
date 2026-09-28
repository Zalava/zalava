package org.zalava.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

public final class FileSystemModuleEnablement implements ModuleEnablement, EnabledModuleRegistry {

  private static final ObjectMapper JSON =
      new ObjectMapper()
          .rebuild()
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();
  private static final TypeReference<List<EnabledModule>> ENABLED_MODULES =
      new TypeReference<>() {};

  private final Path modulesRoot;
  private final Path managedRoot;
  private final Path registry;
  private final FileSystemModuleLifecycleStore lifecycle;

  public FileSystemModuleEnablement(Path workspace) {
    this.managedRoot = workspace.toAbsolutePath().normalize().resolve("source-module-installation");
    this.modulesRoot = managedRoot.resolve("modules");
    this.registry = managedRoot.resolve("enabled-modules.json");
    this.lifecycle = new FileSystemModuleLifecycleStore(workspace);
  }

  @Override
  public synchronized EnablementResult enable(EnabledModule module) {
    validateManagedRoot();
    Path artifact = Path.of(module.artifactPath()).toAbsolutePath().normalize();
    if (!artifact.startsWith(modulesRoot)
        || !Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)) {
      throw new SourceModuleInstallationException(
          "Enabled artifact must be inside the managed module directory");
    }
    validateArtifactIntegrity(artifact, module.artifactDigest());
    module.runtimeArtifacts().forEach(this::validateRuntimeArtifact);
    List<EnabledModule> enabled = new ArrayList<>(enabledModules());
    enabled.removeIf(existing -> existing.moduleId().equals(module.moduleId()));
    enabled.add(
        new EnabledModule(
            module.moduleId(),
            module.version(),
            artifact.toString(),
            module.artifactDigest(),
            module.seaRuntimeCompatibility(),
            module.sourceRepository(),
            module.sourceLicense(),
            module.binaryRepositoryId(),
            module.declaredPermissions(),
            module.runtimeArtifacts().stream()
                .map(
                    runtime ->
                        new ModuleEnablement.RuntimeArtifact(
                            Path.of(runtime.artifactPath()).toAbsolutePath().normalize().toString(),
                            runtime.artifactDigest()))
                .toList()));
    enabled.sort(Comparator.comparing(EnabledModule::moduleId));
    lifecycle.registerNewInstall(module.moduleId());
    persist(enabled);
    return new EnablementResult(registry.toString());
  }

  @Override
  public synchronized DisableResult disable(String moduleId) {
    if (moduleId == null || moduleId.isBlank()) {
      throw new SourceModuleInstallationException("Module id is required");
    }
    List<EnabledModule> enabled = new ArrayList<>(readRegistry());
    boolean changed = enabled.removeIf(module -> module.moduleId().equals(moduleId));
    if (changed) {
      enabled.sort(Comparator.comparing(EnabledModule::moduleId));
      persist(enabled);
    }
    return new DisableResult(moduleId, changed);
  }

  @Override
  public synchronized List<EnabledModule> enabledModules() {
    List<EnabledModule> enabled = readRegistry();
    try {
      enabled.forEach(
          module -> {
            validateArtifactIntegrity(
                Path.of(module.artifactPath()).toAbsolutePath().normalize(),
                module.artifactDigest());
            module.runtimeArtifacts().forEach(this::validateRuntimeArtifact);
          });
      return enabled;
    } catch (SourceModuleInstallationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException("Unable to read enabled module registry", ex);
    }
  }

  private List<EnabledModule> readRegistry() {
    if (!Files.isRegularFile(registry)) {
      return List.of();
    }
    try {
      return List.copyOf(JSON.readValue(registry.toFile(), ENABLED_MODULES));
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException("Unable to read enabled module registry", ex);
    }
  }

  private void persist(List<EnabledModule> enabled) {
    Path temporary = registry.resolveSibling(registry.getFileName() + ".tmp");
    try {
      validateManagedRoot();
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), enabled);
      AtomicFileOperations.replace(temporary, registry);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to persist enabled module registry", ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // Preserve the primary registry failure.
      }
    }
  }

  private void validateManagedRoot() {
    try {
      if (Files.exists(managedRoot, LinkOption.NOFOLLOW_LINKS)
          && Files.isSymbolicLink(managedRoot)) {
        throw new SourceModuleInstallationException(
            "Managed module registry parent must not be a symbolic link");
      }
      Files.createDirectories(managedRoot);
      if (Files.isSymbolicLink(managedRoot)) {
        throw new SourceModuleInstallationException(
            "Managed module registry parent must not be a symbolic link");
      }
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Unable to validate managed module registry parent", ex);
    }
  }

  private void validateArtifactIntegrity(Path artifact, String expectedDigest) {
    try {
      Files.createDirectories(modulesRoot);
      Path realArtifact = artifact.toRealPath();
      if (!realArtifact.startsWith(modulesRoot.toRealPath())) {
        throw new SourceModuleInstallationException(
            "Enabled artifact must be inside the managed module directory");
      }
      String actualDigest = digest(realArtifact);
      if (!actualDigest.equals(expectedDigest)) {
        throw new SourceModuleInstallationException(
            "Enabled artifact digest does not match installed provenance");
      }
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to validate enabled module artifact", ex);
    }
  }

  private void validateRuntimeArtifact(ModuleEnablement.RuntimeArtifact artifact) {
    if (artifact == null || artifact.artifactPath() == null || artifact.artifactDigest() == null) {
      throw new SourceModuleInstallationException("Enabled runtime artifact metadata is required");
    }
    validateArtifactIntegrity(
        Path.of(artifact.artifactPath()).toAbsolutePath().normalize(), artifact.artifactDigest());
  }

  private static String digest(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, read);
        }
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
