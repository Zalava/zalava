package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

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
              JsonNodeFactory.instance.objectNode());
      assertThat(content).isEqualTo(java.util.Map.of("value", "fixture", "configured", "none"));

      Object failure =
          ModuleAssertions.assertToolFails(
              providers,
              ContractFixtureModule.PROVIDER_ID,
              ContractFixtureModule.TOOL_NAME,
              JsonNodeFactory.instance.objectNode().put("fail", true));
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
                      JsonNodeFactory.instance.objectNode()))
          .isInstanceOf(AssertionError.class);
    }
  }
}
