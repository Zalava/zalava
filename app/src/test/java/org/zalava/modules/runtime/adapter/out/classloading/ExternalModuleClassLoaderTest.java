package org.zalava.modules.runtime.adapter.out.classloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.runtime.ExternalSeaModuleLoadingException;

class ExternalModuleClassLoaderTest {

  @TempDir Path workspace;

  @Test
  void singleArgumentLoaderMayOnlyLoadItsRegistryOnce() throws Exception {
    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(List::of)) {
      assertThat(loader.loadModules()).isEmpty();
      assertThatThrownBy(loader::loadModules)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("External SEA modules have already been loaded");
    }
  }

  @Test
  void rejectsTheRetiredPreviewServiceDescriptorBeforeDiscovery() throws Exception {
    Path artifact = workspace.resolve("preview.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
      output.putNextEntry(new JarEntry("META-INF/services/org.zalava.sea.ZalavaModule"));
      output.write("example.LegacyModule".getBytes());
      output.closeEntry();
    }
    ModuleEnablement.EnabledModule module =
        new ModuleEnablement.EnabledModule(
            "preview-module", "1.0.0", artifact.toString(), "sha256:test", ">=0.1.0");

    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(() -> List.of(module))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void explainsHowToMigrateAPreviewServiceDescriptor() {
    assertThat(ExternalModuleClassLoader.previewServiceDescriptorError("example-module"))
        .isEqualTo(
            "External module example-module uses retired preview SPI service descriptor "
                + "META-INF/services/org.zalava.sea.ZalavaModule; rebuild it against "
                + "org.zalava:module-api:1.0.0 and register "
                + "META-INF/services/org.zalava.api.ZalavaModule");
  }

  @Test
  void isolatesAModuleWhoseBundleRepeatsThePrimaryArtifact() throws Exception {
    Path artifact = emptyJar("duplicate.jar");
    ModuleEnablement.EnabledModule module =
        new ModuleEnablement.EnabledModule(
            "duplicate-module",
            "1.0.0",
            artifact.toString(),
            "sha256:test",
            ">=0.1.0",
            null,
            null,
            null,
            List.of(),
            List.of(new ModuleEnablement.RuntimeArtifact(artifact.toString(), "sha256:test")));

    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(() -> List.of(module))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void isolatesAModuleWithAnInvalidCompatibilityRangeBeforeLoadingArtifacts() throws Exception {
    ModuleEnablement.EnabledModule module =
        new ModuleEnablement.EnabledModule(
            "invalid-compatibility-module",
            "1.0.0",
            workspace.resolve("missing.jar").toString(),
            "sha256:test",
            "not-a-range");

    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(() -> List.of(module))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void isolatesAModuleWhoseArtifactIsNotAJar() throws Exception {
    Path artifact = workspace.resolve("not-a-jar.jar");
    Files.writeString(artifact, "not a JAR");
    ModuleEnablement.EnabledModule module =
        new ModuleEnablement.EnabledModule(
            "invalid-archive-module", "1.0.0", artifact.toString(), "sha256:test", ">=0.1.0");

    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(() -> List.of(module))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void loadsAndClosesTheHostFaithfulExternalModuleFixture() throws Exception {
    Path artifact = workspace.resolve("fixture.jar");
    Files.copy(Path.of(System.getProperty("sea.test.external-module-jar")), artifact);
    ModuleEnablement.EnabledModule module =
        new ModuleEnablement.EnabledModule(
            "sea-external-module-fixture",
            "1.0.0",
            artifact.toString(),
            "sha256:test",
            ">=1.0.0 <2.0.0");

    try (ExternalModuleClassLoader loader = new ExternalModuleClassLoader(() -> List.of(module))) {
      assertThat(loader.loadModules()).hasSize(1);
    }
  }

  @Test
  void reportsFailureTypesWithoutIncludingFailureMessages() {
    ExternalSeaModuleLoadingException failure =
        new ExternalSeaModuleLoadingException(
            "secret-value", new IllegalStateException("also-secret"));

    assertThat(ExternalModuleClassLoader.failureTypes(failure))
        .isEqualTo(
            "org.zalava.modules.runtime.ExternalSeaModuleLoadingException"
                + " -> java.lang.IllegalStateException");
  }

  private Path emptyJar(String filename) throws Exception {
    Path artifact = workspace.resolve(filename);
    try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(artifact))) {
      // A valid archive with no module service deliberately reaches bundle validation.
    }
    return artifact;
  }

  @Test
  void allowsThirdPartyBundlePackagesWhileReservingSeaNamespaces() {
    assertThat(
            ExternalModuleClassLoader.requiresExclusiveOwnership(
                "com/fasterxml/jackson/databind/deser"))
        .isFalse();
    assertThat(ExternalModuleClassLoader.requiresExclusiveOwnership("org/zalava/fixture")).isTrue();
  }
}
