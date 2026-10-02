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
import org.zalava.capabilities.discovery.adapter.out.springai.SeaToolCallbackCatalog;
import org.zalava.capabilities.operation.adapter.in.agent.SeaProviderToolInvoker;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.tasks.domain.TaskExecutionContext;

class SeaNativeToolDiscoveryTest {

  private static final ZalavaProvider PROVIDER =
      new ZalavaProvider() {
        private final ProviderDescriptor descriptor =
            new ProviderDescriptor(
                "sea-filesystem-provider",
                "zalava-module-filesystem",
                "filesystem",
                "SEA Filesystem",
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
  void discoversAndCreatesCallbackForSeaNativeProviderWithoutCompatibilityFiltering() {
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
    SeaToolCallbackCatalog callbacks =
        new SeaToolCallbackCatalog(
            catalog, new SeaProviderToolInvoker(null, new TaskExecutionContext()));

    assertThat(discovery.search("scoped file", 5))
        .extracting(match -> match.providerId() + "/" + match.toolName())
        .containsExactly("sea-filesystem-provider/readFile");
    assertThat(callbacks.callback("sea-filesystem-provider", "readFile").getToolDefinition().name())
        .startsWith("sea_readFile_");
  }
}
