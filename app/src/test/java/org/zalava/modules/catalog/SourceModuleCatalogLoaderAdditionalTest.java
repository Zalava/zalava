package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceModuleCatalogLoaderAdditionalTest {

  private final SourceModuleCatalogLoader loader = new SourceModuleCatalogLoader();

  @TempDir Path tempDir;

  @Test
  void rejectsNonObjectRoot() throws Exception {
    Files.writeString(tempDir.resolve("catalog.yaml"), "\"just a string\"\n");
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must be an object");
  }

  @Test
  void rejectsNonStringKeys() throws Exception {
    Files.writeString(tempDir.resolve("catalog.yaml"), "[1, 2]: value\nfoo: bar\n");
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must use string keys");
  }

  @Test
  void rejectsWrongSchemaVersion() throws Exception {
    writeCatalog(
        """
        schemaVersion: 99
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("schemaVersion must be 1");
  }

  @Test
  void rejectsNonStringIndexRepository() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: 123
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must be a non-blank string");
  }

  @Test
  void rejectsNonLowercaseMavenRepoId() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: UPPERCASE
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must be a lowercase repository id");
  }

  @Test
  void rejectsNonHttpsMavenRepositoryUrl() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: ftp://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must be a valid HTTPS URI");
  }

  @Test
  void rejectsNonStringEntryField() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries:
          - moduleId: 123
            path: test.yaml
            sha256: "0000000000000000000000000000000000000000000000000000000000000000"
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("moduleId must be a non-blank string");
  }

  @Test
  void rejectsNonObjectEntry() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries:
          - not-a-map
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("entries[0] must be an object");
  }

  @Test
  void rejectsInvalidEntrySha256() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries:
          - moduleId: test
            path: test.yaml
            sha256: "not-hex"
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("entries[0].sha256 must be a lowercase SHA-256 digest");
  }

  @Test
  void rejectsRepositoryTypeMustBeSource() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: maven
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("repository.type must be source");
  }

  @Test
  void rejectsInvalidHttpsUriSyntax() throws Exception {
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: https://example.com
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    // Already covered: HTTPS validation is tested above
    // Test a different branch - repository with invalid URI
    writeCatalog(
        """
        schemaVersion: 1
        repository:
          type: source
          indexRepository: ":invalid:uri:"
          indexPath: catalog.yaml
        mavenRepositories:
          - repositoryId: central
            url: https://repo.maven.apache.org/maven2
        entries: []
        """);
    assertThatThrownBy(() -> loader.load(tempDir.resolve("catalog.yaml")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must be a valid HTTPS URI");
  }

  @Test
  void loadPathRejectsNonexistentFile() {
    assertThatThrownBy(() -> loader.load(tempDir.resolve("nonexistent.yaml")))
        .isInstanceOf(Exception.class);
  }

  @Test
  void loadPathRejectsNoParentDirectory() {
    // load() resolves to absolute, so "/" has no parent
    assertThatThrownBy(() -> loader.load(Path.of("/")))
        .isInstanceOf(SourceModuleCatalogValidationException.class)
        .hasMessageContaining("must have a parent directory");
  }

  private void writeCatalog(String yaml) throws Exception {
    Files.writeString(tempDir.resolve("catalog.yaml"), yaml);
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(Files.readAllBytes(path));
    return HexFormat.of().formatHex(digest.digest());
  }
}
