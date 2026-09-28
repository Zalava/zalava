package org.zalava.development.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.development.CandidateEvaluation;
import org.zalava.development.InstalledModuleAcceptance;
import org.zalava.development.ModuleDevelopmentContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemInstalledModuleAcceptanceStoreTest {

  @TempDir Path workspace;

  private FileSystemInstalledModuleAcceptanceStore store() {
    return new FileSystemInstalledModuleAcceptanceStore(workspace);
  }

  @Test
  void findReturnsEmptyWhenNoEvidenceFileExists() {
    assertThat(store().find("weather")).isEmpty();
  }

  @Test
  void savePersistsEvidenceAndFindReturnsIt() {
    InstalledModuleAcceptance saved = store().save(acceptance("weather"));

    assertThat(saved).isEqualTo(acceptance("weather"));
    assertThat(store().find("weather")).contains(acceptance("weather"));
    assertThat(store().find("other-module")).isEmpty();
  }

  @Test
  void saveReplacesEvidenceForSameModuleId() {
    store().save(acceptance("weather"));
    InstalledModuleAcceptance updated =
        new InstalledModuleAcceptance(
            "weather",
            "2.0.0",
            contract("weather"),
            contract("weather"),
            List.of(),
            evaluation(),
            List.of(),
            new InstalledModuleAcceptance.SourceMetadata(
                "digest-2", "repo-1", "https://example.test/repo"),
            Instant.parse("2026-09-04T00:00:00Z"));
    store().save(updated);

    assertThat(store().find("weather")).contains(updated);
  }

  @Test
  void saveKeepsEvidenceForOtherModules() {
    store().save(acceptance("weather"));
    store().save(acceptance("calendar"));

    assertThat(store().find("weather")).isPresent();
    assertThat(store().find("calendar")).isPresent();
  }

  @Test
  void findMapsCorruptEvidenceFileToInstallationException() throws IOException {
    Path file = workspace.resolve("source-module-installation/installed-acceptance.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{ not json");

    assertThatThrownBy(() -> store().find("weather"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Unable to read installed acceptance evidence");
  }

  private static InstalledModuleAcceptance acceptance(String moduleId) {
    return new InstalledModuleAcceptance(
        moduleId,
        "1.0.0",
        contract(moduleId),
        contract(moduleId),
        List.of(),
        evaluation(),
        List.of("tool:ping"),
        new InstalledModuleAcceptance.SourceMetadata(
            "digest-1", "repo-1", "https://example.test/repo"),
        Instant.parse("2026-09-03T00:00:00Z"));
  }

  private static CandidateEvaluation evaluation() {
    return new CandidateEvaluation(
        CandidateEvaluation.Decision.ACCEPTED,
        Instant.parse("2026-09-03T00:00:00Z"),
        List.of("scenario s1 passed"),
        "{\"decision\":\"ACCEPTED\"}",
        "# report",
        List.of(),
        List.of());
  }

  private static ModuleDevelopmentContract contract(String moduleId) {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module(moduleId, "1.0.0"),
        "Expose a deterministic ping tool.",
        "1.0.0",
        List.of(
            new ModuleDevelopmentContract.Tool(
                "ping",
                "Pings.",
                "{\"type\":\"object\"}",
                "{\"type\":\"object\"}",
                List.of(),
                List.of())),
        List.of(),
        List.of(
            new ModuleDevelopmentContract.AcceptanceScenario("s1", "{}", List.of(), "PING_FAILED")),
        new ModuleDevelopmentContract.OperationalRequirements(
            null, null, null, null, null, null, null),
        new ModuleDevelopmentContract.DeliveryRequirements(
            "jar", ".*\\.jar", "1", false, Map.of()));
  }
}
