package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Optional;
import org.zalava.FactorySecretAccess;
import org.zalava.ProviderFactoryContext;
import org.junit.jupiter.api.Test;

class ConfigFixtureTest {

  @Test
  void scopesFactoryConfigurationToTheDeclaredFactory() {
    ConfigFixture fixture =
        ConfigFixture.empty()
            .factoryConfiguration("module-a", "factory-a", Map.of("greeting", "hi"));
    ProviderFactoryContext context = fixture.providerContext();

    assertThat(context.forFactory("module-a", "factory-a").configuration())
        .containsEntry("greeting", "hi");
    assertThat(context.forFactory("module-a", "factory-b").configuration()).isEmpty();
    assertThat(context.forFactory("module-b", "factory-a").configuration()).isEmpty();
  }

  @Test
  void scopesHostServicesAndSecretsToTheDeclaredModule() {
    FactorySecretAccess moduleSecret =
        reference -> reference.equals("token") ? Optional.of("s".toCharArray()) : Optional.empty();
    HostService service = new HostService() {};
    ConfigFixture fixture =
        ConfigFixture.empty()
            .factoryConfiguration("module-a", "factory-a", Map.of())
            .hostService("module-a", HostService.class, service)
            .secrets("module-a", moduleSecret);
    ProviderFactoryContext context = fixture.providerContext();

    assertThat(context.forFactory("module-a", "factory-a").service(HostService.class))
        .contains(service);
    assertThat(context.forFactory("module-b", "factory-a").service(HostService.class)).isEmpty();
    assertThat(context.forFactory("module-a", "factory-a").secrets().resolve("token")).isPresent();
    assertThat(context.forFactory("module-a", "factory-a").secrets().resolve("missing")).isEmpty();
  }

  @Test
  void fallsBackToGlobalSecretAccessAndRejectsBlankIdentity() {
    FactorySecretAccess global = reference -> Optional.of((reference + "-global").toCharArray());
    ProviderFactoryContext context = ConfigFixture.empty().secrets(global).providerContext();

    assertThat(context.forFactory("module-a", "factory-a").secrets().resolve("token"))
        .hasValueSatisfying(value -> assertThat(new String(value)).isEqualTo("token-global"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ConfigFixture.empty().factoryConfiguration(" ", "factory", Map.of()))
        .withMessage("moduleId must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ConfigFixture.empty().factoryConfiguration("module", " ", Map.of()))
        .withMessage("factoryId must not be blank");
  }

  private interface HostService {}
}
