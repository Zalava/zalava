package org.zalava.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.zalava.ModuleConfigurationDescriptor;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaModule;
import org.zalava.SeaProvider;
import org.zalava.SeaServiceContract;
import org.zalava.SeaServiceDescriptor;
import org.zalava.SeaServiceFactory;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore.DesiredState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedSeaRuntimeTest {
  private static final AtomicInteger SERVICE_FACTORY_CREATIONS = new AtomicInteger();
  @TempDir Path directory;

  @Test
  void missingConfigurationDoesNotBlockBootAndCanBeAppliedThenStartedLive() {
    FileSystemModuleLifecycleStore lifecycle = new FileSystemModuleLifecycleStore(directory);
    FileSystemModuleConfigurationStore configurations =
        new FileSystemModuleConfigurationStore(directory);
    lifecycle.set("fixture", DesiredState.RUNNING);

    try (ManagedSeaRuntime runtime = runtime(lifecycle, configurations)) {
      assertThat(runtime.modules()).hasSize(1);
      assertThat(runtime.activeModules()).isEmpty();
      assertThat(runtime.state("fixture").state())
          .isEqualTo(ManagedSeaRuntime.State.SETUP_REQUIRED);

      configurations.saveCandidate(snapshot("ready"), Map.of());
      runtime.applyCandidate("fixture");
      runtime.start("fixture");
      assertThat(runtime.activeModules()).hasSize(1);
      assertThat(runtime.state("fixture").state()).isEqualTo(ManagedSeaRuntime.State.RUNNING);

      configurations.saveCandidate(snapshot("broken"), Map.of());
      assertThatThrownBy(() -> runtime.applyCandidate("fixture"))
          .isInstanceOf(IllegalStateException.class);
      assertThat(configurations.active("fixture").orElseThrow().factories())
          .isEqualTo(snapshot("ready").factories());
      assertThat(runtime.activeModules()).hasSize(1);

      runtime.stop("fixture");
      assertThat(runtime.activeModules()).isEmpty();
      assertThat(runtime.state("fixture").state()).isEqualTo(ManagedSeaRuntime.State.STOPPED);
    }
  }

  @Test
  void anEnabledServiceModuleWithoutConfigurationDoesNotBlockSeaStartup() {
    SERVICE_FACTORY_CREATIONS.set(0);
    FileSystemModuleLifecycleStore lifecycle = new FileSystemModuleLifecycleStore(directory);
    lifecycle.set("service-fixture", DesiredState.RUNNING);

    ManagedSeaRuntime runtime =
        new ManagedSeaRuntime(
            new StaticSeaModuleRegistry(List.of(new ServiceFixtureModule())),
            Set.of(),
            List::of,
            lifecycle,
            new FileSystemModuleConfigurationStore(directory),
            ProviderFactoryContext.empty());

    assertThat(runtime.state("service-fixture").state())
        .isEqualTo(ManagedSeaRuntime.State.SETUP_REQUIRED);
    assertThat(runtime.loadedProviders()).isEmpty();
    assertThat(SERVICE_FACTORY_CREATIONS).hasValue(0);
  }

  private ManagedSeaRuntime runtime(
      FileSystemModuleLifecycleStore lifecycle, FileSystemModuleConfigurationStore configurations) {
    return new ManagedSeaRuntime(
        new StaticSeaModuleRegistry(List.of(new FixtureModule())),
        Set.of(),
        List::of,
        lifecycle,
        configurations,
        ProviderFactoryContext.empty());
  }

  private static ModuleConfigurationSnapshot snapshot(String endpoint) {
    return new ModuleConfigurationSnapshot(
        "fixture",
        "1.0.0",
        "fixture-schema",
        Map.of("fixture-factory", Map.of("endpoint", endpoint)),
        Map.of());
  }

  private static final class FixtureModule implements SeaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor("fixture", "1.0.0", "Fixture", "Fixture module");
    }

    @Override
    public ModuleConfigurationDescriptor configuration() {
      return new ModuleConfigurationDescriptor(
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "fixture-factory",
                  Map.of(
                      "type",
                      "object",
                      "properties",
                      Map.of("endpoint", Map.of("type", "string"))))));
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of(
          new ProviderFactory() {
            @Override
            public ProviderFactoryDescriptor descriptor() {
              return new ProviderFactoryDescriptor(
                  "fixture-factory", "fixture", "fixture", "Fixture", "Fixture");
            }

            @Override
            public List<SeaProvider> createProviders(ProviderFactoryContext context) {
              Object endpoint = context.configuration().get("endpoint");
              if (endpoint == null || endpoint.equals("broken")) {
                throw new IllegalStateException("Fixture requires a working endpoint");
              }
              return List.of();
            }
          });
    }
  }

  private static final class ServiceFixtureModule implements SeaModule {
    private static final SeaServiceContract<AutoCloseable> CONTRACT =
        new SeaServiceContract<>("fixture-service", "1", AutoCloseable.class);

    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor("service-fixture", "1.0.0", "Service fixture", "Service fixture");
    }

    @Override
    public ModuleConfigurationDescriptor configuration() {
      return new ModuleConfigurationDescriptor(
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "services",
                  Map.of(
                      "type",
                      "object",
                      "required",
                      List.of("endpoint"),
                      "properties",
                      Map.of("endpoint", Map.of("type", "string", "minLength", 1))))));
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<SeaServiceFactory<?>> serviceFactories() {
      return List.of(
          new SeaServiceFactory<AutoCloseable>() {
            @Override
            public SeaServiceDescriptor descriptor() {
              return new SeaServiceDescriptor("fixture-service", "service-fixture", "1");
            }

            @Override
            public SeaServiceContract<AutoCloseable> contract() {
              return CONTRACT;
            }

            @Override
            public AutoCloseable create(org.zalava.SeaServiceFactoryContext context) {
              SERVICE_FACTORY_CREATIONS.incrementAndGet();
              throw new IllegalStateException("service endpoint is required");
            }
          });
    }
  }
}
