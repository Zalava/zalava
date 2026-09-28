package org.zalava.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.discovery.adapter.out.springai.SeaToolCallbackCatalog;
import org.zalava.operation.adapter.in.agent.SeaProviderToolInvoker;
import org.zalava.operation.application.port.out.ProviderCatalog;
import org.zalava.tasks.domain.TaskExecutionContext;

class SeaNativeToolDiscoveryTest {

  private static final SeaProvider PROVIDER =
      new SeaProvider() {
        private final ProviderDescriptor descriptor =
            new ProviderDescriptor(
                "sea-filesystem-provider",
                "sea-module-filesystem",
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
        public List<SeaToolDescriptor> listTools() {
          return List.of(
              new SeaToolDescriptor(
                  "readFile", "Read a scoped file.", false, List.of("filesystem"), Map.of()));
        }
      };

  @Test
  void discoversAndCreatesCallbackForSeaNativeProviderWithoutCompatibilityFiltering() {
    ProviderCatalog catalog =
        new ProviderCatalog() {
          @Override
          public List<SeaProvider> providers() {
            return List.of(PROVIDER);
          }

          @Override
          public Optional<SeaProvider> findProvider(String providerId) {
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
