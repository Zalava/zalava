package org.zalava.modules.sample;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.api.testing.ModuleContractKit;

class SampleModuleTest {
  @Test
  void loadsActualModuleJarThroughPublicKit() throws Exception {
    try (var kit =
        ModuleContractKit.load(
            Path.of(System.getProperty("module.artifact")),
            List.of(),
            "zalava-module-sample",
            System.getProperty("module.version"))) {
      assertEquals("zalava-module-sample", kit.moduleId());
      assertNotNull(kit.module().providerFactories());
      try (var providers = kit.providers()) {
        assertNotNull(providers);
      }
    }
  }
}
