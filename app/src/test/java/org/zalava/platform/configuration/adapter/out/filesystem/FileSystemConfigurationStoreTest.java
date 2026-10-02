package org.zalava.platform.configuration.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemConfigurationStoreTest {

  @TempDir Path temporaryDirectory;

  @Test
  void returnsAnEmptyConfigurationWhenTheFileDoesNotExist() throws Exception {
    var store = new FileSystemConfigurationStore(temporaryDirectory.resolve("missing.yaml"));

    assertThat(store.read()).isEmpty();
  }

  @Test
  void writesAndReadsNestedYamlConfiguration() throws Exception {
    Path configurationPath = temporaryDirectory.resolve("config/application.private.yaml");
    var store = new FileSystemConfigurationStore(configurationPath);
    Map<String, Object> configuration =
        Map.of("agent", Map.of("onboarding", Map.of("completed", true)));

    store.write(configuration);

    assertThat(store.read())
        .containsEntry("agent", Map.of("onboarding", Map.of("completed", true)));
  }

  @Test
  void resolvesAnExplicitDirectoryToThePrivateConfigurationFile() {
    var store = FileSystemConfigurationStore.fromLocation(temporaryDirectory + "/");

    assertThat(store).isNotNull();
  }

  @Test
  void resolvesAnExplicitDirectoryToThePrivateConfigurationPath() {
    assertThat(FileSystemConfigurationStore.pathFromLocation(temporaryDirectory + "/"))
        .isEqualTo(temporaryDirectory.resolve("application.private.yaml"));
  }

  @Test
  void resolvesTheFilesystemImportAfterClasspathImports() {
    assertThat(
            FileSystemConfigurationStore.pathFromImport(
                "optional:classpath:application.private.yaml,optional:file:"
                    + temporaryDirectory
                    + "/application.private.yaml"))
        .isEqualTo(temporaryDirectory.resolve("application.private.yaml"));
  }
}
