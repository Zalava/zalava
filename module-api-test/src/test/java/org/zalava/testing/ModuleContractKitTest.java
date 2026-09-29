package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleContractKitTest {

  @TempDir Path workspace;

  @Test
  void exercisesAModuleAvailableOnTheTestClasspath() throws IOException {
    ContractFixtureModule module = new ContractFixtureModule();

    try (ModuleContractKit kit = ModuleContractKit.of(module)) {
      assertThat(kit.module()).isSameAs(module);
      assertThat(kit.moduleId()).isEqualTo(ContractFixtureModule.MODULE_ID);
      assertThat(kit.version()).isEqualTo("1.0.0");
      assertThat(kit.providers().providers()).hasSize(1);
      assertThat(kit.services().factories()).isEmpty();
      assertThat(kit.webExtensions().routes()).isEmpty();
    }
  }

  @Test
  void loadsARealArtifactModuleAndClosesIt() throws Exception {
    Path artifact = syntheticModuleJar("fixture-artifact-module", "2.0.0");

    try (ModuleContractKit kit =
        ModuleContractKit.load(artifact, List.of(), "fixture-artifact-module", "2.0.0")) {
      assertThat(kit.moduleId()).isEqualTo("fixture-artifact-module");
      assertThat(kit.version()).isEqualTo("2.0.0");
      assertThat(kit.providers().providers()).isEmpty();
    }
  }

  @Test
  void closesTheArtifactLoaderWhenTheModuleIdentityDoesNotMatch() throws Exception {
    Path artifact = syntheticModuleJar("fixture-artifact-module", "2.0.0");

    assertThatThrownBy(() -> ModuleContractKit.load(artifact, List.of(), "other-module", "2.0.0"))
        .isInstanceOf(ExternalModuleTestHarnessException.class)
        .hasMessageContaining("other-module");
  }

  private Path syntheticModuleJar(String moduleId, String version) throws IOException {
    Path classes = workspace.resolve(moduleId + "/classes");
    Path source = workspace.resolve(moduleId + "/src/fixture/artifact/ArtifactModule.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package fixture.artifact;"
            + " import java.util.List;"
            + " import org.zalava.ModuleDescriptor;"
            + " import org.zalava.ProviderFactory;"
            + " import org.zalava.ZalavaModule;"
            + " public final class ArtifactModule implements ZalavaModule {"
            + " public ModuleDescriptor descriptor() { return new ModuleDescriptor(\""
            + moduleId
            + "\", \""
            + version
            + "\", \"Artifact fixture\", \"fixture\"); }"
            + " public List<ProviderFactory> providerFactories() { return List.of(); } }",
        StandardCharsets.UTF_8);
    compile(classes, List.of(source));
    return jar(
        classes,
        workspace.resolve(moduleId + "/module.jar"),
        List.of("META-INF/services/org.zalava.ZalavaModule=fixture.artifact.ArtifactModule\n"));
  }

  private static void compile(Path output, List<Path> sources) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("a JDK compiler is required for fixture jars").isNotNull();
    Files.createDirectories(output);
    List<String> arguments = new ArrayList<>();
    arguments.addAll(
        List.of("-classpath", System.getProperty("java.class.path"), "-d", output.toString()));
    sources.stream().map(Path::toString).forEach(arguments::add);
    assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).isZero();
  }

  private static Path jar(Path classes, Path artifact, List<String> extraEntries)
      throws IOException {
    Files.createDirectories(classes);
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
      try (var paths = Files.walk(classes)) {
        paths
            .filter(Files::isRegularFile)
            .forEach(
                path -> {
                  try {
                    String name = classes.relativize(path).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(name));
                    Files.copy(path, output);
                    output.closeEntry();
                  } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                  }
                });
      }
      for (String entry : extraEntries) {
        String[] parts = entry.split("=", 2);
        output.putNextEntry(new JarEntry(parts[0]));
        output.write(parts[1].getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    } catch (UncheckedIOException exception) {
      throw exception.getCause();
    }
    return artifact;
  }
}
