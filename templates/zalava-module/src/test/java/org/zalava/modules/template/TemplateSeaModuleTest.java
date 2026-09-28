package org.zalava.modules.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.zalava.SeaModule;

class TemplateSeaModuleTest {

  @Test
  void registersALoadableModuleWithOneProviderFactory() {
    SeaModule module = ServiceLoader.load(SeaModule.class).findFirst().orElseThrow();

    assertEquals(TemplateSeaModule.MODULE_ID, module.descriptor().moduleId());
    assertEquals("1.0.0-SNAPSHOT", module.descriptor().version());
    assertFalse(module.providerFactories().isEmpty());
  }
}
