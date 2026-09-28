package org.zalava.testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.zalava.SeaModule;

/**
 * Entry point for exercising an external module at the stable {@code module-api} boundary without a
 * SEA instance. It complements {@link ExternalModuleTestHarness}, which owns artifact isolation and
 * service discovery, with helpers that instantiate providers and services from the loaded module.
 *
 * <p>The kit asserts only module-owned behavior for a supplied scope. Host-owned resolution,
 * validation, permissions, approvals, persistence and transport stay covered by SEA's own tests.
 */
public final class ModuleContractKit implements AutoCloseable {
  private final ExternalModuleTestHarness harness;
  private final SeaModule module;

  private ModuleContractKit(ExternalModuleTestHarness harness, SeaModule module) {
    this.harness = harness;
    this.module = module;
  }

  /**
   * Loads a real module artifact under the isolated classloader and selects the expected module.
   */
  public static ModuleContractKit load(
      Path primaryArtifact,
      List<Path> runtimeArtifacts,
      String expectedModuleId,
      String expectedVersion) {
    Objects.requireNonNull(primaryArtifact, "primaryArtifact");
    Objects.requireNonNull(runtimeArtifacts, "runtimeArtifacts");
    ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(primaryArtifact, runtimeArtifacts);
    try {
      return new ModuleContractKit(
          harness, harness.loadModule(expectedModuleId, expectedVersion).module());
    } catch (RuntimeException exception) {
      closeQuietly(harness);
      throw exception;
    }
  }

  /** Exercises a module already available on the test classpath, without an artifact. */
  public static ModuleContractKit of(SeaModule module) {
    return new ModuleContractKit(null, Objects.requireNonNull(module, "module"));
  }

  public SeaModule module() {
    return module;
  }

  public String moduleId() {
    return module.descriptor().moduleId();
  }

  public String version() {
    return module.descriptor().version();
  }

  public ProviderFixture providers() {
    return providers(ConfigFixture.empty());
  }

  public ProviderFixture providers(ConfigFixture configuration) {
    return ProviderFixture.create(module, configuration);
  }

  public ServiceFixture services() {
    return services(ConfigFixture.empty());
  }

  public ServiceFixture services(ConfigFixture configuration) {
    return ServiceFixture.create(module, configuration);
  }

  public WebExtensionFixture webExtensions() {
    return WebExtensionFixture.register(module);
  }

  @Override
  public void close() throws IOException {
    closeQuietly(harness);
  }

  private static void closeQuietly(ExternalModuleTestHarness harness) {
    if (harness == null) {
      return;
    }
    try {
      harness.close();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to close module artifact classloader", exception);
    }
  }
}
