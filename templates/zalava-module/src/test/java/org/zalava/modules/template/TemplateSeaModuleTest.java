package org.zalava.modules.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.zalava.ZalavaModule;

class TemplateZalavaModuleTest {

  @Test
  void registersALoadableModuleWithOneProviderFactory() {
    ZalavaModule module = ServiceLoader.load(ZalavaModule.class).findFirst().orElseThrow();

    assertEquals(TemplateZalavaModule.MODULE_ID, module.descriptor().moduleId());
    assertEquals("1.0.0-SNAPSHOT", module.descriptor().version());
    assertFalse(module.providerFactories().isEmpty());
  }
}
