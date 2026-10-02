package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.ModuleConfigurationStatus;

class FileSystemModuleConfigurationStoreTest {

  @TempDir Path workspace;

  private FileSystemModuleConfigurationStore store() {
    return new FileSystemModuleConfigurationStore(workspace);
  }

  private static ModuleConfigurationSnapshot snapshot(String moduleId, String version) {
    return new ModuleConfigurationSnapshot(moduleId, version, "schema-1", Map.of(), Map.of());
  }

  @Test
  void saveAndPromoteCandidate() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of("secret-key", "secret-val"));
    assertThat(s.candidate("m1")).isPresent();
    assertThat(s.active("m1")).isEmpty();
    s.promoteCandidate("m1");
    assertThat(s.active("m1")).isPresent();
    assertThat(s.candidate("m1")).isEmpty();
  }

  @Test
  void promoteCandidatesPromotesAllWithCandidates() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.saveCandidate(snapshot("m2", "2.0.0"), Map.of());
    s.promoteCandidates();
    assertThat(s.active("m1")).isPresent();
    assertThat(s.active("m2")).isPresent();
  }

  @Test
  void promoteCandidatesHandlesMissingRoot() {
    var s = store();
    s.promoteCandidates();
  }

  @Test
  void promoteCandidateMovesExistingActiveToRollback() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    s.saveCandidate(snapshot("m1", "1.1.0"), Map.of());
    s.promoteCandidate("m1");
    assertThat(s.active("m1").orElseThrow().version()).isEqualTo("1.1.0");
  }

  @Test
  void rollbackRestoresPreviousActive() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    s.saveCandidate(snapshot("m1", "1.1.0"), Map.of());
    s.promoteCandidate("m1");
    s.rollback("m1");
    assertThat(s.active("m1").orElseThrow().version()).isEqualTo("1.0.0");
  }

  @Test
  void rollbackFailsWhenNoRollback() {
    var s = store();
    assertThatThrownBy(() -> s.rollback("nonexistent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No candidate configuration");
  }

  @Test
  void quarantineCandidateMovesToQuarantine() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.quarantineCandidate("m1");
    assertThat(s.status("m1")).isEqualTo(ModuleConfigurationStatus.CONFIGURATION_INVALID);
    assertThat(s.candidate("m1")).isEmpty();
  }

  @Test
  void cancelCandidateRemovesCandidate() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.cancelCandidate("m1");
    assertThat(s.candidate("m1")).isEmpty();
    assertThat(s.status("m1")).isEqualTo(ModuleConfigurationStatus.SETUP_REQUIRED);
  }

  @Test
  void statusReportsRestartRequiredWhenCandidateExists() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    assertThat(s.status("m1")).isEqualTo(ModuleConfigurationStatus.RESTART_REQUIRED);
  }

  @Test
  void statusReportsActiveWhenOnlyActiveExists() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    assertThat(s.status("m1")).isEqualTo(ModuleConfigurationStatus.ACTIVE);
  }

  @Test
  void statusReportsSetupRequiredWhenNothingExists() {
    var s = store();
    assertThat(s.status("nonexistent")).isEqualTo(ModuleConfigurationStatus.SETUP_REQUIRED);
  }

  @Test
  void secretsReturnsAccessForActiveConfig() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of("k", "val"));
    s.promoteCandidate("m1");
    var access = s.secrets("m1");
    assertThat(access.resolve("k")).isPresent();
    assertThat(new String(access.resolve("k").get())).isEqualTo("val");
    assertThat(access.resolve("missing")).isEmpty();
  }

  @Test
  void candidateSecretsReturnsAccessForCandidate() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of("ck", "cval"));
    var access = s.candidateSecrets("m1");
    assertThat(access.resolve("ck")).isPresent();
  }

  @Test
  void secretsReturnsEmptyForNonexistentModule() {
    var s = store();
    assertThat(s.secrets("none").resolve("any")).isEmpty();
  }

  @Test
  void activeConfigurationsReturnsAllActive() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.saveCandidate(snapshot("m2", "2.0.0"), Map.of());
    s.promoteCandidate("m1");
    s.promoteCandidate("m2");
    var configs = s.activeConfigurations();
    assertThat(configs).hasSize(2);
    assertThat(configs).containsKey("m1");
    assertThat(configs).containsKey("m2");
  }

  @Test
  void activeConfigurationsEmptyWhenRootMissing() {
    var s = store();
    assertThat(s.activeConfigurations()).isEmpty();
  }

  @Test
  void promoteCandidateHandlesCorruptRollback() throws Exception {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    // Create an invalid rollback directory
    Path rollback = workspace.resolve("module-configuration/m1/rollback/snapshot.json");
    Files.createDirectories(rollback.getParent());
    Files.writeString(rollback, "not-json");
    s.saveCandidate(snapshot("m1", "1.1.0"), Map.of());
    // promoteCandidate will try to delete the corrupt rollback first
    s.promoteCandidate("m1");
    assertThat(s.active("m1").orElseThrow().version()).isEqualTo("1.1.0");
  }

  @Test
  void cancelCandidateHandlesNonexistent() {
    var s = store();
    // cancelCandidate should handle a non-existent candidate gracefully
    // It calls delete which calls deletePath which checks Files.exists
    s.cancelCandidate("nonexistent");
  }

  @Test
  void secretsHandlesCorruptSecretsFile() throws Exception {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of("k", "v"));
    s.promoteCandidate("m1");
    // Corrupt the secrets file
    Path secretsFile = workspace.resolve("module-configuration/m1/active/secrets.json");
    Files.writeString(secretsFile, "not-json");
    var access = s.secrets("m1");
    assertThatThrownBy(() -> access.resolve("k")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void candidateSecretsHandlesNonexistentCandidate() {
    var s = store();
    assertThat(s.candidateSecrets("none").resolve("k")).isEmpty();
  }

  @Test
  void saveCreatesSecretsEvenWithEmptyMap() {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    assertThat(s.candidate("m1")).isPresent();
  }

  @Test
  void promoteCandidateHandlesExistingRollbackDirectory() throws Exception {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    // Create rollback dir manually
    Path rollback = workspace.resolve("module-configuration/m1/rollback");
    Files.createDirectories(rollback);
    Files.writeString(rollback.resolve("snapshot.json"), "{}");
    Files.writeString(rollback.resolve("secrets.json"), "{}");
    s.saveCandidate(snapshot("m1", "1.1.0"), Map.of());
    s.promoteCandidate("m1");
    // Should have moved candidate to active, old active to rollback
    assertThat(s.active("m1").orElseThrow().version()).isEqualTo("1.1.0");
  }

  @Test
  void activeHandlesCorruptSnapshot() throws Exception {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    s.promoteCandidate("m1");
    Path snapshotFile = workspace.resolve("module-configuration/m1/active/snapshot.json");
    Files.writeString(snapshotFile, "not-json");
    assertThatThrownBy(() -> s.active("m1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to read module configuration");
  }

  @Test
  void candidateHandlesCorruptSnapshot() throws Exception {
    var s = store();
    s.saveCandidate(snapshot("m1", "1.0.0"), Map.of());
    Path snapshotFile = workspace.resolve("module-configuration/m1/candidate/snapshot.json");
    Files.writeString(snapshotFile, "not-json");
    assertThatThrownBy(() -> s.candidate("m1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to read module configuration");
  }
}
