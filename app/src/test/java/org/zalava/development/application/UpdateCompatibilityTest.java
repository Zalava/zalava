package org.zalava.development.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.zalava.development.CandidateEvaluation;
import org.zalava.development.InstalledModuleAcceptance;
import org.zalava.development.ModuleDevelopmentContract;
import org.junit.jupiter.api.Test;

class UpdateCompatibilityTest {
  @Test
  void permitsOptionalAdditionsWithoutApproval() {
    assertThat(decision(contract("{\"type\":\"object\"}", "{}"), List.of("filesystem:read")))
        .isEqualTo(UpdateCompatibility.Decision.COMPATIBLE);
  }

  @Test
  void requiresApprovalForNewPermissions() {
    assertThat(
            decision(
                contract("{\"type\":\"object\"}", "{}"), List.of("filesystem:read", "network")))
        .isEqualTo(UpdateCompatibility.Decision.REQUIRES_APPROVAL);
  }

  @Test
  void rejectsRemovedToolsAndIncompatibleSchemas() {
    ModuleDevelopmentContract removed = contractWithTools(List.of());
    assertThat(decision(removed, List.of("filesystem:read")))
        .isEqualTo(UpdateCompatibility.Decision.BREAKING);
    assertThat(decision(contract("{\"type\":\"string\"}", "{}"), List.of("filesystem:read")))
        .isEqualTo(UpdateCompatibility.Decision.BREAKING);
  }

  @Test
  void marksFailedStandaloneValidationInvalid() {
    assertThat(
            UpdateCompatibility.compare(
                    installed(), contract("{\"type\":\"object\"}", "{}"), report(false), List.of())
                .decision())
        .isEqualTo(UpdateCompatibility.Decision.INVALID);
  }

  private static UpdateCompatibility.Decision decision(
      ModuleDevelopmentContract candidate, List<String> permissions) {
    return UpdateCompatibility.compare(installed(), candidate, report(true), permissions)
        .decision();
  }

  private static InstalledModuleAcceptance installed() {
    ModuleDevelopmentContract contract = contract("{\"type\":\"object\"}", "{}");
    return new InstalledModuleAcceptance(
        "sea-module-example",
        "1.0.0",
        contract,
        contract,
        contract.acceptanceScenarios(),
        report(true),
        List.of("filesystem:read"),
        new InstalledModuleAcceptance.SourceMetadata(
            "sha256:" + "0".repeat(64), "local-private", null),
        Instant.EPOCH);
  }

  private static CandidateEvaluation report(boolean accepted) {
    return new CandidateEvaluation(
        accepted, Instant.EPOCH, List.of("test"), "{}", "# report", List.of());
  }

  private static ModuleDevelopmentContract contract(String input, String output) {
    return contractWithTools(
        List.of(
            new ModuleDevelopmentContract.Tool(
                "lookup",
                "Lookup",
                input,
                output,
                List.of(),
                List.of(new ModuleDevelopmentContract.ExampleCall("{}", "{}")))));
  }

  private static ModuleDevelopmentContract contractWithTools(
      List<ModuleDevelopmentContract.Tool> tools) {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("sea-module-example", "1.0.1"),
        "test",
        "1",
        tools.isEmpty()
            ? List.of(
                new ModuleDevelopmentContract.Tool(
                    "other",
                    "Other",
                    "{}",
                    "{}",
                    List.of(),
                    List.of(new ModuleDevelopmentContract.ExampleCall("{}", "{}"))))
            : tools,
        List.of(),
        List.of(
            new ModuleDevelopmentContract.AcceptanceScenario(
                "scenario",
                "{}",
                List.of(new ModuleDevelopmentContract.ResponseAssertion("$", "exists", "true")),
                null)),
        new ModuleDevelopmentContract.OperationalRequirements(
            null, null, false, List.of(), false, false, "25"),
        new ModuleDevelopmentContract.DeliveryRequirements("jar", "*.jar", "1", false, Map.of()));
  }
}
