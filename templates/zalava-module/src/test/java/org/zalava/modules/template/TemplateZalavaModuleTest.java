package org.zalava.modules.template;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.api.testing.ModuleContractKit;

class TemplateZalavaModuleTest {
  @Test
  void loadsActualModuleJarThroughPublicKit() throws Exception {
    try (var kit =
        ModuleContractKit.load(
            Path.of(System.getProperty("module.artifact")),
            List.of(),
            "zalava-module-template",
            System.getProperty("module.version"))) {
      assertEquals("zalava-module-template", kit.moduleId());
      assertNotNull(kit.module().providerFactories());
      try (var providers = kit.providers()) {
        assertEquals(1, providers.providers().size());
        var provider = providers.providers().getFirst();
        assertEquals("template", provider.descriptor().providerId());
        assertNotNull(provider.capabilities());
        assertTrue(provider.listTools().isEmpty());
      }
    }
  }
}
