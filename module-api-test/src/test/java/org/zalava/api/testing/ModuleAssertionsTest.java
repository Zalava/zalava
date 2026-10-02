package org.zalava.api.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleAssertionsTest {

  @Test
  void assertsToolResultsAndReturnsContent() {
    try (ProviderFixture providers =
        ModuleContractKit.of(new ContractFixtureModule()).providers()) {
      Object content =
          ModuleAssertions.assertToolSucceeds(
              providers,
              ContractFixtureModule.PROVIDER_ID,
              ContractFixtureModule.TOOL_NAME,
              Map.of());
      assertThat(content).isEqualTo(java.util.Map.of("value", "fixture", "configured", "none"));

      Object failure =
          ModuleAssertions.assertToolFails(
              providers,
              ContractFixtureModule.PROVIDER_ID,
              ContractFixtureModule.TOOL_NAME,
              Map.of("fail", true));
      assertThat(failure).isEqualTo(java.util.Map.of("status", "FIXTURE_FAILURE"));
    }
  }

  @Test
  void surfacesAnUnexpectedResultAsAnAssertionFailure() {
    try (ProviderFixture providers =
        ModuleContractKit.of(new ContractFixtureModule()).providers()) {
      assertThatThrownBy(
              () ->
                  ModuleAssertions.assertToolFails(
                      providers,
                      ContractFixtureModule.PROVIDER_ID,
                      ContractFixtureModule.TOOL_NAME,
                      Map.of()))
          .isInstanceOf(AssertionError.class);
    }
  }
}
