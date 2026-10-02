package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;

class ModuleReleaseIndexClientTest {
  @Test
  void rejectsNonHttpsAndAmbiguousUrlsBeforeARequest() {
    ModuleReleaseIndexClient client = new ModuleReleaseIndexClient();

    assertThatThrownBy(
            () -> client.load(URI.create("http://example.test/releases/index.yaml"), "token"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("HTTPS URL");
    assertThatThrownBy(
            () ->
                client.load(
                    URI.create("https://example.test/releases/index.yaml?ref=main"), "token"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("without query or fragment");
  }
}
