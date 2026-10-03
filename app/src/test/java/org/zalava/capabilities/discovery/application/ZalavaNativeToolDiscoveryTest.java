package org.zalava.capabilities.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackCatalog;
import org.zalava.capabilities.operation.adapter.in.agent.ZalavaProviderToolInvoker;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.tasks.domain.TaskExecutionContext;

class ZalavaNativeToolDiscoveryTest {

  private static final ZalavaProvider PROVIDER =
      new ZalavaProvider() {
        private final ProviderDescriptor descriptor =
            new ProviderDescriptor(
                "zalava-filesystem-provider",
                "zalava-module-filesystem",
                "filesystem",
                "Zalava Filesystem",
                "Scoped files.",
                "1.0.0",
                ProviderCapabilities.toolsOnly(),
                List.of("filesystem"),
                Map.of());

        @Override
        public ProviderDescriptor descriptor() {
          return descriptor;
        }

        @Override
        public ProviderCapabilities capabilities() {
          return descriptor.capabilities();
        }

        @Override
        public List<ZalavaToolDescriptor> listTools() {
          return List.of(
              new ZalavaToolDescriptor(
                  "readFile", "Read a scoped file.", false, List.of("filesystem"), Map.of()));
        }
      };

  @Test
  void discoversAndCreatesCallbackForZalavaNativeProviderWithoutCompatibilityFiltering() {
    ProviderCatalog catalog =
        new ProviderCatalog() {
          @Override
          public List<ZalavaProvider> providers() {
            return List.of(PROVIDER);
          }

          @Override
          public Optional<ZalavaProvider> findProvider(String providerId) {
            return providerId.equals(PROVIDER.descriptor().providerId())
                ? Optional.of(PROVIDER)
                : Optional.empty();
          }
        };

    DefaultInstalledToolDiscovery discovery = new DefaultInstalledToolDiscovery(catalog);
    ZalavaToolCallbackCatalog callbacks =
        new ZalavaToolCallbackCatalog(
            catalog, new ZalavaProviderToolInvoker(null, new TaskExecutionContext()));

    assertThat(discovery.search("scoped file", 5))
        .extracting(match -> match.providerId() + "/" + match.toolName())
        .containsExactly("zalava-filesystem-provider/readFile");
    assertThat(
            callbacks.callback("zalava-filesystem-provider", "readFile").getToolDefinition().name())
        .startsWith("zalava_readFile_");
    assertThat(callbacks.callbacks()).hasSize(1);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> callbacks.callback("zalava-filesystem-provider", "missing"))
        .isInstanceOf(java.util.NoSuchElementException.class)
        .hasMessageContaining("zalava-filesystem-provider/missing");
  }
}
