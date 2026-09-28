package org.zalava.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaModule;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.runtime.application.port.out.RuntimeModuleRegistry;
import org.junit.jupiter.api.Test;

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

  private record TestModule(List<String> closed) implements SeaModule {
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
            public List<SeaProvider> createProviders(ProviderFactoryContext context) {
              return List.of(
                  new SeaProvider() {
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
                    public List<SeaToolDescriptor> listTools() {
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
