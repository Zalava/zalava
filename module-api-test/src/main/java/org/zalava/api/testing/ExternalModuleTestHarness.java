package org.zalava.api.testing;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ZalavaModule;

/**
 * Loads an external module under Zalava's isolated module classloader boundary for repository
 * tests.
 *
 * <p>The host supplies the stable {@link ZalavaModule} API through the parent classloader. Every
 * supplied artifact is loaded only by one child loader, matching the current single-module
 * production boundary.
 */
public final class ExternalModuleTestHarness implements AutoCloseable {

  private final URLClassLoader classLoader;

  private ExternalModuleTestHarness(URLClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public static ExternalModuleTestHarness load(Path primaryArtifact, List<Path> runtimeArtifacts) {
    List<Path> artifacts = validatedArtifacts(primaryArtifact, runtimeArtifacts);
    try {
      URL[] urls = artifacts.stream().map(ExternalModuleTestHarness::toUrl).toArray(URL[]::new);
      return new ExternalModuleTestHarness(
          new URLClassLoader(urls, ZalavaModule.class.getClassLoader()));
    } catch (RuntimeException exception) {
      throw exception;
    }
  }

  public LoadedModule loadModule(String expectedModuleId, String expectedVersion) {
    requireText(expectedModuleId, "expected module id");
    requireText(expectedVersion, "expected module version");

    List<ZalavaModule> modules = new ArrayList<>();
    try {
      ServiceLoader.load(ZalavaModule.class, classLoader).stream()
          .map(ServiceLoader.Provider::get)
          .forEach(modules::add);
    } catch (ServiceConfigurationError | RuntimeException exception) {
      throw new ExternalModuleTestHarnessException(
          "Unable to load external Zalava module service", exception);
    }

    if (modules.size() != 1) {
      throw new ExternalModuleTestHarnessException(
          "Expected exactly one external Zalava module service but discovered " + modules.size());
    }

    ZalavaModule module = modules.getFirst();
    ModuleDescriptor descriptor;
    try {
      descriptor = module.descriptor();
    } catch (LinkageError | RuntimeException exception) {
      throw new ExternalModuleTestHarnessException(
          "Unable to read external Zalava module descriptor", exception);
    }
    if (descriptor == null) {
      throw new ExternalModuleTestHarnessException(
          "External Zalava module descriptor must not be null");
    }
    if (!expectedModuleId.equals(descriptor.moduleId())) {
      throw new ExternalModuleTestHarnessException(
          "Expected external Zalava module "
              + expectedModuleId
              + " but discovered "
              + descriptor.moduleId());
    }
    if (!expectedVersion.equals(descriptor.version())) {
      throw new ExternalModuleTestHarnessException(
          "Expected external Zalava module version "
              + expectedVersion
              + " but discovered "
              + descriptor.version());
    }
    return new LoadedModule(module, classLoader);
  }

  @Override
  public void close() throws IOException {
    classLoader.close();
  }

  private static List<Path> validatedArtifacts(Path primaryArtifact, List<Path> runtimeArtifacts) {
    if (primaryArtifact == null) {
      throw new IllegalArgumentException("primary artifact must not be null");
    }
    if (runtimeArtifacts == null) {
      throw new IllegalArgumentException("runtime artifacts must not be null");
    }
    List<Path> artifacts = new ArrayList<>();
    artifacts.add(regularFile(primaryArtifact, "primary artifact"));
    for (Path runtimeArtifact : runtimeArtifacts) {
      artifacts.add(regularFile(runtimeArtifact, "runtime artifact"));
    }
    LinkedHashSet<Path> identities = new LinkedHashSet<>(artifacts);
    if (identities.size() != artifacts.size()) {
      throw new IllegalArgumentException("module artifact paths must be unique");
    }
    return List.copyOf(artifacts);
  }

  private static Path regularFile(Path artifact, String description) {
    if (artifact == null) {
      throw new IllegalArgumentException(description + " must not be null");
    }
    Path normalized = artifact.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized)) {
      throw new IllegalArgumentException(
          description + " must be an existing regular file: " + normalized);
    }
    return normalized;
  }

  private static URL toUrl(Path artifact) {
    try {
      return artifact.toUri().toURL();
    } catch (IOException exception) {
      throw new ExternalModuleTestHarnessException(
          "Unable to resolve module artifact URL", exception);
    }
  }

  private static void requireText(String value, String description) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
  }

  public record LoadedModule(ZalavaModule module, ClassLoader moduleClassLoader) {}
}
