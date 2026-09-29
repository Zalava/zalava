package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.ZalavaModule;

class ExternalModuleTestHarnessTest {

  @TempDir Path workspace;

  @Test
  void discoversTheModuleServiceWithTheApiLoadedFromTheHostParent() throws Exception {
    Fixture fixture = fixture(false);

    try (ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(fixture.moduleJar(), List.of())) {
      ExternalModuleTestHarness.LoadedModule loaded = harness.loadModule("fixture-module", "1.0.0");

      assertThat(loaded.module().getClass().getClassLoader()).isSameAs(loaded.moduleClassLoader());
      assertThat(loaded.moduleClassLoader().getParent())
          .isSameAs(ZalavaModule.class.getClassLoader());
      assertThat(loaded.moduleClassLoader().loadClass(ZalavaModule.class.getName()))
          .isSameAs(ZalavaModule.class);
    }
  }

  @Test
  void failsWhenTheModuleNeedsAnUndeclaredRuntimeJar() throws Exception {
    Fixture fixture = fixture(true);

    try (ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(fixture.moduleJar(), List.of())) {
      assertThatThrownBy(() -> harness.loadModule("fixture-module", "1.0.0"))
          .isInstanceOf(ExternalModuleTestHarnessException.class)
          .hasMessage("Unable to read external SEA module descriptor")
          .hasCauseInstanceOf(NoClassDefFoundError.class);
    }
  }

  @Test
  void loadsTheModuleWhenItsRuntimeJarIsDeclared() throws Exception {
    Fixture fixture = fixture(true);

    try (ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(fixture.moduleJar(), List.of(fixture.runtimeJar()))) {
      assertThat(harness.loadModule("fixture-module", "1.0.0").module().descriptor().displayName())
          .isEqualTo("fixture runtime");
    }
  }

  @Test
  void rejectsDuplicateArtifactIdentityBeforeCreatingTheClassLoader() throws Exception {
    Fixture fixture = fixture(true);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ExternalModuleTestHarness.load(
                    fixture.moduleJar(), List.of(fixture.runtimeJar(), fixture.runtimeJar())))
        .withMessage("module artifact paths must be unique");
  }

  @Test
  void rejectsThePrimaryArtifactRepeatedAsARuntimeArtifact() throws Exception {
    Fixture fixture = fixture(false);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> ExternalModuleTestHarness.load(fixture.moduleJar(), List.of(fixture.moduleJar())))
        .withMessage("module artifact paths must be unique");
  }

  @Test
  void rejectsArtifactsThatAreMissingOrNull() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExternalModuleTestHarness.load(null, List.of()))
        .withMessage("primary artifact must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExternalModuleTestHarness.load(workspace, List.of()))
        .withMessageContaining("primary artifact must be an existing regular file");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExternalModuleTestHarness.load(workspace, null))
        .withMessage("runtime artifacts must not be null");
  }

  @Test
  void rejectsMissingOrNullRuntimeArtifacts() throws Exception {
    Fixture fixture = fixture(false);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExternalModuleTestHarness.load(fixture.moduleJar(), List.of(workspace)))
        .withMessageContaining("runtime artifact must be an existing regular file");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ExternalModuleTestHarness.load(
                    fixture.moduleJar(), java.util.Arrays.asList((Path) null)))
        .withMessage("runtime artifact must not be null");
  }

  @Test
  void rejectsMissingServicesAndUnexpectedModuleIdentity() throws Exception {
    Path emptyJar = jar(workspace.resolve("empty"), workspace.resolve("empty.jar"), List.of());
    try (ExternalModuleTestHarness harness = ExternalModuleTestHarness.load(emptyJar, List.of())) {
      assertThatThrownBy(() -> harness.loadModule("fixture-module", "1.0.0"))
          .isInstanceOf(ExternalModuleTestHarnessException.class)
          .hasMessage("Expected exactly one external SEA module service but discovered 0");
    }

    Fixture fixture = fixture(false);
    try (ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(fixture.moduleJar(), List.of())) {
      assertThatThrownBy(() -> harness.loadModule("other-module", "1.0.0"))
          .isInstanceOf(ExternalModuleTestHarnessException.class)
          .hasMessage("Expected external SEA module other-module but discovered fixture-module");
      assertThatThrownBy(() -> harness.loadModule("fixture-module", "2.0.0"))
          .isInstanceOf(ExternalModuleTestHarnessException.class)
          .hasMessage("Expected external SEA module version 2.0.0 but discovered 1.0.0");
    }
  }

  @Test
  void rejectsBlankExpectedModuleIdentityAndSupportsMessageOnlyFailures() {
    assertThat(new ExternalModuleTestHarnessException("message").getMessage()).isEqualTo("message");
  }

  @Test
  void rejectsBlankExpectedModuleIdentity() throws Exception {
    Fixture fixture = fixture(false);
    try (ExternalModuleTestHarness harness =
        ExternalModuleTestHarness.load(fixture.moduleJar(), List.of())) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> harness.loadModule(" ", "1.0.0"))
          .withMessage("expected module id must not be blank");
      assertThatIllegalArgumentException()
          .isThrownBy(() -> harness.loadModule("fixture-module", " "))
          .withMessage("expected module version must not be blank");
    }
  }

  @Test
  void rejectsAModuleWithNoDescriptor() throws Exception {
    Path classes = workspace.resolve("null-descriptor/classes");
    Path source = workspace.resolve("null-descriptor/src/fixture/module/NullDescriptorModule.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package fixture.module; import java.util.List; import org.zalava.ProviderFactory;"
            + " import org.zalava.ZalavaModule; public final class NullDescriptorModule implements ZalavaModule {"
            + " public org.zalava.ModuleDescriptor descriptor() { return null; }"
            + " public List<ProviderFactory> providerFactories() { return List.of(); } }",
        StandardCharsets.UTF_8);
    compile(classes, List.of(source), List.of());
    Path artifact =
        jar(
            classes,
            workspace.resolve("null-descriptor/module.jar"),
            List.of(
                "META-INF/services/org.zalava.ZalavaModule=fixture.module.NullDescriptorModule\n"));

    try (ExternalModuleTestHarness harness = ExternalModuleTestHarness.load(artifact, List.of())) {
      assertThatThrownBy(() -> harness.loadModule("fixture-module", "1.0.0"))
          .isInstanceOf(ExternalModuleTestHarnessException.class)
          .hasMessage("External SEA module descriptor must not be null");
    }
  }

  @Test
  void exposesNoSpringOrSeaApplicationClassesThroughTheHostParent() {
    ClassLoader hostParent = ZalavaModule.class.getClassLoader();

    assertThatThrownBy(() -> hostParent.loadClass("org.springframework.context.ApplicationContext"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> hostParent.loadClass("org.zalava.runtime.ExternalSeaModuleLoader"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  private Fixture fixture(boolean needsRuntime) throws Exception {
    Path sourceRoot = workspace.resolve(needsRuntime ? "with-runtime" : "without-runtime");
    Path runtimeClasses = sourceRoot.resolve("runtime-classes");
    Path runtimeSource = sourceRoot.resolve("runtime-src/fixture/runtime/RuntimeName.java");
    Files.createDirectories(runtimeSource.getParent());
    Files.writeString(
        runtimeSource,
        "package fixture.runtime; public final class RuntimeName {"
            + " public static String value() { return \"fixture runtime\"; } }",
        StandardCharsets.UTF_8);
    compile(runtimeClasses, List.of(runtimeSource), List.of());
    Path runtimeJar = jar(runtimeClasses, sourceRoot.resolve("fixture-runtime.jar"), List.of());

    Path moduleClasses = sourceRoot.resolve("module-classes");
    Path moduleSource = sourceRoot.resolve("module-src/fixture/module/FixtureModule.java");
    Files.createDirectories(moduleSource.getParent());
    String displayName =
        needsRuntime ? "fixture.runtime.RuntimeName.value()" : "\"fixture module\"";
    Files.writeString(
        moduleSource,
        "package fixture.module;"
            + " import java.util.List;"
            + " import org.zalava.ModuleDescriptor;"
            + " import org.zalava.ProviderFactory;"
            + " import org.zalava.ZalavaModule;"
            + " public final class FixtureModule implements ZalavaModule {"
            + " public ModuleDescriptor descriptor() { return new ModuleDescriptor(\"fixture-module\", \"1.0.0\", "
            + displayName
            + ", \"fixture\"); }"
            + " public List<ProviderFactory> providerFactories() { return List.of(); } }",
        StandardCharsets.UTF_8);
    compile(moduleClasses, List.of(moduleSource), needsRuntime ? List.of(runtimeJar) : List.of());
    Path moduleJar =
        jar(
            moduleClasses,
            sourceRoot.resolve("fixture-module.jar"),
            List.of("META-INF/services/org.zalava.ZalavaModule=fixture.module.FixtureModule\n"));
    return new Fixture(moduleJar, runtimeJar);
  }

  private static void compile(Path output, List<Path> sources, List<Path> classpath)
      throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("a JDK compiler is required for fixture jars").isNotNull();
    Files.createDirectories(output);
    String currentClasspath = System.getProperty("java.class.path");
    String fixtureClasspath =
        classpath.stream()
            .map(Path::toString)
            .reduce(currentClasspath, (left, right) -> left + ":" + right);
    List<String> arguments = new java.util.ArrayList<>();
    arguments.addAll(List.of("-classpath", fixtureClasspath, "-d", output.toString()));
    sources.stream().map(Path::toString).forEach(arguments::add);
    int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
    assertThat(result).isZero();
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

  private record Fixture(Path moduleJar, Path runtimeJar) {}
}
