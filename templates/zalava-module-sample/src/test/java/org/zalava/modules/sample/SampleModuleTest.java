package org.zalava.modules.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zalava.testing.ModuleContractKit;

class SampleModuleTest {
  @Test
  void resolvesThePublicApiAndContractKitWithoutCredentials() {
    var module = new SampleModule();

    assertThat(ModuleContractKit.of(module).moduleId()).isEqualTo("zalava-module-sample");
  }
}
