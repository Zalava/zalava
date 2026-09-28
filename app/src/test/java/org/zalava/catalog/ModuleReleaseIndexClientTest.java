package org.zalava.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.junit.jupiter.api.Test;

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
