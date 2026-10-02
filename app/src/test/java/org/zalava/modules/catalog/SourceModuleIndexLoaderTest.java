package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceModuleIndexLoaderTest {

  private final SourceModuleIndexLoader loader = new SourceModuleIndexLoader();

  @Test
  void loadsValidatedSourceModuleEntry() {
    SourceModuleIndex index = loader.load(validIndex());

    assertThat(index.schemaVersion()).isEqualTo(1);
    assertThat(index.modules()).hasSize(1);
    SourceModuleIndex.Module module = index.modules().getFirst();
    assertThat(module.moduleId()).isEqualTo("zalava-module-files");
    assertThat(module.supportUrl()).hasToString("https://github.com/example/zalava-module-files");
    assertThat(module.source().repository())
        .hasToString("https://github.com/example/zalava-module-files.git");
    assertThat(module.build().command()).containsExactly("./gradlew", "build");
    assertThat(module.factories())
        .extracting(SourceModuleIndex.Factory::factoryId)
        .containsExactly("workspace-files");
    assertThat(module.operations())
        .extracting(SourceModuleIndex.Operation::name)
        .containsExactly("readFile");
    assertThat(module.operations().getFirst().inputSchema()).containsEntry("type", "object");
    assertThat(module.configurationSchema()).containsEntry("type", "object");
    assertThat(module.security().permissions()).containsExactly("file.read");
  }

  @Test
  void loadsCheckedInTimeModuleIndexEntry() throws Exception {
    SourceModuleIndex index =
        loader.load(Path.of("..", "docs", "source-modules", "zalava-module-time.yaml"));

    assertThat(index.schemaVersion()).isEqualTo(1);
    assertThat(index.modules()).hasSize(1);
    SourceModuleIndex.Module module = index.modules().getFirst();
    assertThat(module.moduleId()).isEqualTo("zalava-module-time");
    assertThat(module.source().repository())
        .hasToString("https://github.com/Zalava/zalava-module-time.git");
    assertThat(module.build().command()).containsExactly("./gradlew", "build");
    assertThat(module.build().verificationCommand()).containsExactly("./gradlew", "test");
    assertThat(module.factories())
        .extracting(SourceModuleIndex.Factory::factoryId)
        .containsExactly("jdk-time");
    assertThat(module.operations())
        .extracting(SourceModuleIndex.Operation::name)
        .containsExactly("current_time", "convert_time");
    assertThat(module.operations())
        .allSatisfy(operation -> assertThat(operation.sideEffecting()).isFalse());
    assertThat(module.security().permissions()).isEmpty();
  }

  @Test
  void loadsCheckedInShoppingListModuleIndexEntry() throws Exception {
    SourceModuleIndex index =
        loader.load(Path.of("..", "docs", "source-modules", "zalava-module-shopping-list.yaml"));

    assertThat(index.schemaVersion()).isEqualTo(1);
    assertThat(index.modules()).hasSize(1);
    SourceModuleIndex.Module module = index.modules().getFirst();
    assertThat(module.moduleId()).isEqualTo("zalava-module-shopping-list");
    assertThat(module.source().repository())
        .hasToString("https://github.com/Zalava/zalava-module-shopping-list.git");
    assertThat(module.build().command()).containsExactly("./gradlew", "build");
    assertThat(module.build().verificationCommand()).containsExactly("./gradlew", "test");
    assertThat(module.factories())
        .extracting(SourceModuleIndex.Factory::factoryId)
        .containsExactly("shopping-list-household");
    assertThat(module.operations())
        .extracting(SourceModuleIndex.Operation::name)
        .containsExactly(
            "add_item", "list_items", "mark_bought", "remove_item", "purchase_summary");
    assertThat(module.operations())
        .filteredOn(SourceModuleIndex.Operation::sideEffecting)
        .extracting(SourceModuleIndex.Operation::name)
        .containsExactly("add_item", "mark_bought", "remove_item");
    assertThat(module.security().permissions()).containsExactly("local-storage");
  }

  @Test
  void rejectsUnsupportedSchemaVersion() {
    assertThatThrownBy(
            () -> loader.load(validIndex().replace("schemaVersion: 1", "schemaVersion: 2")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("schemaVersion must be 1");
  }

  @Test
  void rejectsNonHttpsRepository() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validIndex()
                        .replace(
                            "https://github.com/example/zalava-module-files.git",
                            "ssh://git@github.com/example/zalava-module-files.git")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].source.repository must be a valid HTTPS URI");
  }

  @Test
  void rejectsOperationWithoutExplicitSideEffectClassification() {
    assertThatThrownBy(
            () -> loader.load(validIndex().replace("        sideEffecting: false\n", "")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].operations[0].sideEffecting must be a boolean");
  }

  @Test
  void rejectsOperationWithoutInputSchema() {
    assertThatThrownBy(
            () ->
                loader.load(
                    validIndex()
                        .replace(
                            """
                        inputSchema:
                          type: object
                          required: [path]
                          properties:
                            path:
                              type: string
                """,
                            "")))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[0].operations[0].inputSchema must be an object");
  }

  @Test
  void rejectsDuplicateModuleIds() {
    String duplicate = validIndex().replace("modules:\n", "modules:\n" + validModule());

    assertThatThrownBy(() -> loader.load(duplicate))
        .isInstanceOf(SourceModuleIndexValidationException.class)
        .hasMessage("modules[1].moduleId must be unique");
  }

  private static String validIndex() {
    return "schemaVersion: 1\nmodules:\n" + validModule();
  }

  private static String validModule() {
    return """
                  - moduleId: zalava-module-files
                    version: 1.0.0
                    displayName: Files
                    description: Workspace-bound file operations
                    supportUrl: https://github.com/example/zalava-module-files
                    artifact:
                      groupId: ai.sea.modules
                      artifactId: zalava-module-files
                      version: 1.0.0
                    source:
                      repository: https://github.com/example/zalava-module-files.git
                      license: Apache-2.0
                    build:
                      command: ["./gradlew", "build"]
                      verificationCommand: ["./gradlew", "test"]
                    compatibility:
                      seaRuntime: ">=0.1.0"
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
                          required: [path]
                          properties:
                            path:
                              type: string
                    security:
                      permissions: [file.read]
                """;
  }
}
