package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceModuleCatalogLoaderTest {

  private final SourceModuleCatalogLoader loader = new SourceModuleCatalogLoader();

  @TempDir private Path tempDir;

  @Test
  void loadsCheckedInCatalogWithPinnedIndexDigest() throws Exception {
    SourceModuleCatalog catalog =
        loader.load(Path.of("..", "docs", "source-modules", "catalog.yaml"));

    assertThat(catalog.schemaVersion()).isEqualTo(1);
    assertThat(catalog.repository().type()).isEqualTo("source");
    assertThat(catalog.repository().indexRepository())
        .isEqualTo("https://github.com/Zalava/zalava");
    assertThat(catalog.repository().indexPath()).isEqualTo("docs/source-modules/catalog.yaml");
    assertThat(catalog.mavenRepositories())
        .containsExactly(
            new SourceModuleCatalog.MavenRepository(
                "maven-central", "https://repo.maven.apache.org/maven2"));
    assertThat(catalog.entries()).hasSize(2);
    assertThat(catalog.entries())
        .extracting(SourceModuleCatalog.Entry::moduleId)
        .containsExactly("zalava-module-shopping-list", "zalava-module-time");
    assertThat(catalog.entries())
        .extracting(SourceModuleCatalog.Entry::path)
        .containsExactly("zalava-module-shopping-list.yaml", "zalava-module-time.yaml");
    assertThat(catalog.entries())
        .extracting(SourceModuleCatalog.Entry::sha256)
        .containsExactly(
            "ebce1897383c79d8bc38be78c20a23bda1e7bfc269e993d69ae412ceeb476798",
            "7d8c5d23af820101ed8a6859d5ee7fd636cd460e1cf67d35a2f7fc5152721bfe");
    assertThat(catalog.entries())
        .allSatisfy(
            entry ->
                assertThat(entry.index().modules())
                    .extracting(SourceModuleIndex.Module::moduleId)
                    .containsExactly(entry.moduleId()));
  }

  @Test
  void rejectsDigestMismatch() throws Exception {
    Path index = writeIndex("zalava-module-files.yaml", "zalava-module-files");
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """));

    assertThat(index).isRegularFile();
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[0].sha256 must match referenced index content");
  }

  @Test
  void rejectsPathWithDotSegments() throws Exception {
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-files
                    path: ../zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """));

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[0].path must not contain dot segments");
  }

  @Test
  void rejectsMalformedDigest() throws Exception {
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: ABC
                """));

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[0].sha256 must be a lowercase SHA-256 digest");
  }

  @Test
  void rejectsDuplicateModuleIds() throws Exception {
    Path index = writeIndex("zalava-module-files.yaml", "zalava-module-files");
    String digest = sha256(index);
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: %s
                  - moduleId: zalava-module-files
                    path: copy.yaml
                    sha256: %s
                """
                .formatted(digest, digest)));
    Files.copy(index, tempDir.resolve("copy.yaml"));

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[1].moduleId must be unique");
  }

  @Test
  void rejectsCatalogEntryMissingFromReferencedIndex() throws Exception {
    Path index = writeIndex("zalava-module-files.yaml", "zalava-module-files");
    String digest = sha256(index);
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-time
                    path: zalava-module-files.yaml
                    sha256: %s
                """
                .formatted(digest)));

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[0].moduleId must be present in referenced index");
  }

  @Test
  void rejectsInvalidReferencedIndex() throws Exception {
    Path index = tempDir.resolve("broken.yaml");
    Files.writeString(index, "schemaVersion: 1\nmodules: []\n");
    writeCatalog(
        catalog(
            """
                  - moduleId: zalava-module-files
                    path: broken.yaml
                    sha256: %s
                """
                .formatted(sha256(index))));

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("entries[0].index must reference a valid source module index");
  }

  @Test
  void rejectsNonSourceRepositoryMode() throws Exception {
    writeCatalog(
        """
                schemaVersion: 1
                repository:
                  type: maven
                  indexRepository: https://github.com/example/zalava-modules
                  indexPath: catalog.yaml
                entries:
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """);

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("repository.type must be source");
  }

  @Test
  void rejectsNonHttpsIndexRepository() throws Exception {
    writeCatalog(
        """
                schemaVersion: 1
                repository:
                  type: source
                  indexRepository: ssh://git@github.com/example/zalava-modules.git
                  indexPath: catalog.yaml
                entries:
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """);

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("repository.indexRepository must be a valid HTTPS URI");
  }

  @Test
  void rejectsIndexRepositoryPathWithDotSegments() throws Exception {
    writeCatalog(
        """
                schemaVersion: 1
                repository:
                  type: source
                  indexRepository: https://github.com/example/zalava-modules
                  indexPath: ../catalog.yaml
                entries:
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """);

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("repository.indexPath must not contain dot segments");
  }

  @Test
  void rejectsDuplicateMavenRepositoryIds() throws Exception {
    writeCatalog(
        """
                schemaVersion: 1
                repository:
                  type: source
                  indexRepository: https://github.com/example/zalava-modules
                  indexPath: catalog.yaml
                mavenRepositories:
                  - repositoryId: maven-central
                    url: https://repo.maven.apache.org/maven2
                  - repositoryId: maven-central
                    url: https://repo.example.com/maven2
                entries:
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """);

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("mavenRepositories[1].repositoryId must be unique");
  }

  @Test
  void rejectsNonHttpsMavenRepositoryUrls() throws Exception {
    writeCatalog(
        """
                schemaVersion: 1
                repository:
                  type: source
                  indexRepository: https://github.com/example/zalava-modules
                  indexPath: catalog.yaml
                mavenRepositories:
                  - repositoryId: maven-central
                    url: http://repo.maven.apache.org/maven2
                entries:
                  - moduleId: zalava-module-files
                    path: zalava-module-files.yaml
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                """);

    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessage("mavenRepositories[0].url must be a valid HTTPS URI");
  }

  private Path writeCatalog(String yaml) throws Exception {
    Path path = tempDir.resolve("catalog.yaml");
    Files.writeString(path, yaml);
    return path;
  }

  private Path writeIndex(String fileName, String moduleId) throws Exception {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, validIndex(moduleId));
    return path;
  }

  private static String catalog(String entries) {
    return """
                schemaVersion: 1
                repository:
                  type: source
                  indexRepository: https://github.com/example/zalava-modules
                  indexPath: catalog.yaml
                mavenRepositories:
                  - repositoryId: maven-central
                    url: https://repo.maven.apache.org/maven2
                entries:
                %s
                """
        .formatted(entries);
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(Files.readAllBytes(path));
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String validIndex(String moduleId) {
    return """
                schemaVersion: 1
                modules:
                  - moduleId: %s
                    version: 1.0.0
                    displayName: Files
                    description: Workspace-bound file operations
                    supportUrl: https://github.com/example/zalava-module-files
                    artifact:
                      groupId: ai.zalava.modules
                      artifactId: zalava-module-files
                      version: 1.0.0
                    source:
                      repository: https://github.com/example/zalava-module-files.git
                      license: Apache-2.0
                    build:
                      command: ["./gradlew", "build"]
                      verificationCommand: ["./gradlew", "test"]
                    compatibility:
                      zalavaRuntime: ">=0.1.0"
                    configurationSchema:
                      type: object
                    factories:
                      - factoryId: workspace-files
                        providerType: filesystem-root
                    operations:
                      - name: readFile
                        description: Read a workspace file
                        sideEffecting: false
                        inputSchema:
                          type: object
                    security:
                      permissions: [file.read]
                """
        .formatted(moduleId);
  }
}
