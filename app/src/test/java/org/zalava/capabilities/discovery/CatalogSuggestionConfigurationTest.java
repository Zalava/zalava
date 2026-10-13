package org.zalava.capabilities.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

class CatalogSuggestionConfigurationTest {
  @Test
  void explicitDisableDoesNotReadCatalogOrImplyNoMatch() {
    ModuleLocatorReleaseLocator locator =
        new ModuleLocatorReleaseLocator() {
          public List<Module> modules() {
            throw new AssertionError("Disabled catalog must not be read");
          }

          public ResolvedModule resolve(String id) {
            throw new AssertionError("No release resolution");
          }
        };
    assertThat(
            new CatalogSuggestionConfiguration()
                .moduleSuggestions(locator, false)
                .suggest("jobs")
                .status())
        .isEqualTo(ModuleSuggestions.Status.DISABLED);
  }
}
