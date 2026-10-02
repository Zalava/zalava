package org.zalava.api.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaToolDescriptor;

class ProviderFixtureTest {

  @Test
  void createsProvidersAndExercisesToolsWithScopedConfiguration() {
    ContractFixtureModule module = new ContractFixtureModule();
    ConfigFixture configuration =
        ConfigFixture.empty()
            .factoryConfiguration(
                ContractFixtureModule.MODULE_ID, "fixture-factory", Map.of("greeting", "hola"));

    try (ProviderFixture fixture = ModuleContractKit.of(module).providers(configuration)) {
      assertThat(fixture.providers()).hasSize(1);
      assertThat(
              fixture.requireProvider(ContractFixtureModule.PROVIDER_ID).descriptor().providerId())
          .isEqualTo(ContractFixtureModule.PROVIDER_ID);
      assertThat(fixture.tools(ContractFixtureModule.PROVIDER_ID))
          .extracting(ZalavaToolDescriptor::name)
          .containsExactly(ContractFixtureModule.TOOL_NAME);

      ZalavaOperationResult result =
          fixture.invoke(
              ContractFixtureModule.PROVIDER_ID, ContractFixtureModule.TOOL_NAME, Map.of());
      assertThat(result.success()).isTrue();
      assertThat(result.content()).isEqualTo(Map.of("value", "fixture", "configured", "hola"));

      ZalavaOperationResult failure =
          fixture.invoke(
              ContractFixtureModule.PROVIDER_ID,
              ContractFixtureModule.TOOL_NAME,
              Map.of("fail", true));
      assertThat(failure.success()).isFalse();
      assertThat(failure.content()).isEqualTo(Map.of("status", "FIXTURE_FAILURE"));
    }
    assertThat(module.closed[0]).isTrue();
  }

  @Test
  void rejectsUnknownProvidersAndTools() {
    try (ProviderFixture fixture = ModuleContractKit.of(new ContractFixtureModule()).providers()) {
      assertThat(fixture.provider("missing")).isEmpty();
      assertThatIllegalArgumentException()
          .isThrownBy(() -> fixture.requireProvider("missing"))
          .withMessage("Module does not provide provider: missing");
      assertThatIllegalArgumentException()
          .isThrownBy(() -> fixture.requireTool(ContractFixtureModule.PROVIDER_ID, "missing"))
          .withMessageContaining("does not declare tool");
    }
  }
}
