package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior tests for {@link LocalArtifactModuleMetadataLoader} — the parser for the metadata file
 * shipped alongside a locally developed SEA artifact. Covers the happy path and every structural
 * rejection the loader performs.
 */
class LocalArtifactModuleMetadataLoaderTest {
  @TempDir Path temporaryDirectory;

  private final LocalArtifactModuleMetadataLoader loader = new LocalArtifactModuleMetadataLoader();

  @Test
  void loadsValidatedLocalModuleMetadata() {
    SourceModuleIndex index = loader.load(validMetadata());

    assertThat(index.schemaVersion()).isEqualTo(1);
    assertThat(index.modules()).hasSize(1);
    SourceModuleIndex.Module module = index.modules().getFirst();
    assertThat(module.moduleId()).isEqualTo("zalava-module-local");
    assertThat(module.version()).isEqualTo("1.2.3");
    assertThat(module.source()).isNull();
    assertThat(module.build().command()).isEmpty();
    assertThat(module.build().verificationCommand()).isEmpty();
    assertThat(module.compatibility().seaRuntime()).isEqualTo(">=1.0.0");
    assertThat(module.configurationSchema()).containsEntry("type", "object");
    assertThat(module.factories())
        .extracting(SourceModuleIndex.Factory::factoryId)
        .containsExactly("local-factory");
    assertThat(module.security().permissions()).containsExactly("file.read");
  }

  @Test
  void loadsMetadataEmbeddedInUploadedJar() throws Exception {
    Path jar = temporaryDirectory.resolve("module.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-metadata.yaml"));
      output.write(validMetadata().getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    assertThat(loader.loadJar(jar).modules()).hasSize(1);
  }

  @Test
  void rejectsUploadedJarWithRetiredZalavaModuleSpiDescriptor() throws Exception {
    Path jar = temporaryDirectory.resolve("retired-spi.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-metadata.yaml"));
      output.write(validMetadata().getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new JarEntry("META-INF/services/org.zalava.sea.ZalavaModule"));
      output.write("example.RetiredModule".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }

    assertThatThrownBy(() -> loader.loadJar(jar))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("uploaded JAR must not use the retired ZalavaModule SPI descriptor");
  }

  @Test
  void operationWithoutDescriptionFallsBackToName() {
    SourceModuleIndex index =
        loader.load(validMetadata().replace("        description: Read a file\n", ""));

    assertThat(index.modules().getFirst().operations().getFirst().description())
        .isEqualTo("readFile");
  }

  @Test
  void operationWithoutSideEffectingDefaultsToFalse() {
    SourceModuleIndex index =
        loader.load(validMetadata().replace("        sideEffecting: true\n", ""));

    assertThat(index.modules().getFirst().operations().getFirst().sideEffecting()).isFalse();
  }

  @Test
  void emptyPermissionsListIsAllowed() {
    SourceModuleIndex index =
        loader.load(validMetadata().replace("permissions: [file.read]", "permissions: []"));

    assertThat(index.modules().getFirst().security().permissions()).isEmpty();
  }

  @Test
  void rejectsInvalidYaml() {
    // SnakeYAML tolerates most junk; a duplicate anchor name is a hard error.
    assertThatThrownBy(() -> loader.load("schemaVersion: [\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("metadata must be valid YAML");
  }

  @Test
  void rejectsNonObjectRoot() {
    assertThatThrownBy(() -> loader.load("- just\n- a\n- list\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("metadata must be an object");
  }

  @Test
  void rejectsUnsupportedSchemaVersion() {
    assertThatThrownBy(
            () -> loader.load(validMetadata().replace("schemaVersion: 1", "schemaVersion: 2")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("schemaVersion must be 1");
  }

  @Test
  void rejectsNonIntegerSchemaVersion() {
    assertThatThrownBy(
            () -> loader.load(validMetadata().replace("schemaVersion: 1", "schemaVersion: one")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("schemaVersion must be an integer");
  }

  @Test
  void rejectsEmptyModulesList() {
    assertThatThrownBy(() -> loader.load("schemaVersion: 1\nmodules: []\n"))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules must not be empty");
  }

  @Test
  void rejectsDuplicateModuleIds() {
    String duplicate = validMetadata().replace("modules:\n", "modules:\n" + validModule());

    assertThatThrownBy(() -> loader.load(duplicate))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[1].moduleId must be unique");
  }

  @Test
  void rejectsArtifactVersionMismatch() {
    String mismatch =
        validMetadata()
            .replaceFirst("(artifactId: zalava-module-local\\n\\s+version: )1\\.2\\.3", "$19.9.9");

    assertThatThrownBy(() -> loader.load(mismatch))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].artifact.version must match module version");
  }

  @Test
  void rejectsNonHttpsSupportUrl() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validMetadata()
                        .replace(
                            "https://github.com/example/zalava-module-local",
                            "http://github.com/example/zalava-module-local")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].supportUrl must be a valid HTTPS URI");
  }

  @Test
  void rejectsNonObjectConfigurationSchema() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validMetadata()
                        .replaceFirst(
                            "configurationSchema:\\n\\s+type: object",
                            "configurationSchema: scalar")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].configurationSchema must be an object");
  }

  @Test
  void rejectsNonObjectFactory() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validMetadata().replace("- factoryId: local-factory", "- broken: true")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessageContaining("factoryId");
  }

  private static String validMetadata() {
    return "schemaVersion: 1\nmodules:\n" + validModule();
  }

  private static String validModule() {
    return """
                  - moduleId: zalava-module-local
                    version: 1.2.3
                    displayName: Local Fixture
                    description: Locally developed fixture module
                    supportUrl: https://github.com/example/zalava-module-local
                    artifact:
                      groupId: ai.sea.modules
                      artifactId: zalava-module-local
                      version: 1.2.3
                    compatibility:
                      seaRuntime: ">=1.0.0"
                    configurationSchema:
                      type: object
                    factories:
                      - factoryId: local-factory
                        providerType: filesystem-root
                    operations:
                      - name: readFile
                        description: Read a file
                        sideEffecting: true
                        inputSchema:
                          type: object
                    security:
                      permissions: [file.read]
                """;
  }
}
