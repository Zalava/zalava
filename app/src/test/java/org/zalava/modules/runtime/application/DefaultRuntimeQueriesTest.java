package org.zalava.modules.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.modules.runtime.application.port.out.RuntimeModuleRegistry;

class DefaultRuntimeQueriesTest {

  @Test
  void instantiatesAndClosesProvidersWithoutAdapterDependencies() {
    List<String> closed = new java.util.ArrayList<>();
    RuntimeModuleRegistry registry = () -> List.of(new TestModule(closed));
    DefaultRuntimeQueries queries =
        new DefaultRuntimeQueries(registry, ProviderFactoryContext.empty());

    assertThat(queries.findProvider("test-provider")).isPresent();
    assertThat(queries.providers()).hasSize(1);

    queries.close();

    assertThat(closed).containsExactly("test-provider");
  }

  private record TestModule(List<String> closed) implements ZalavaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor("test-module", "1.0.0", "Test", "Test module");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of(
          new ProviderFactory() {
            @Override
            public ProviderFactoryDescriptor descriptor() {
              return new ProviderFactoryDescriptor(
                  "test-factory", "test-module", "test", "Test", "Test factory");
            }

            @Override
            public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
              return List.of(
                  new ZalavaProvider() {
                    @Override
                    public ProviderDescriptor descriptor() {
                      return new ProviderDescriptor(
                          "test-provider",
                          "test-module",
                          "test",
                          "Test",
                          "Test provider",
                          "1.0.0",
                          ProviderCapabilities.toolsOnly(),
                          List.of(),
                          java.util.Map.of());
                    }

                    @Override
                    public ProviderCapabilities capabilities() {
                      return ProviderCapabilities.toolsOnly();
                    }

                    @Override
                    public List<ZalavaToolDescriptor> listTools() {
                      return List.of();
                    }

                    @Override
                    public void close() {
                      closed.add("test-provider");
                    }
                  });
            }
          });
    }
  }
}
