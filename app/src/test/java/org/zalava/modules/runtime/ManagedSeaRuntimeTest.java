package org.zalava.modules.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaServiceFactory;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.modules.runtime.application.port.out.ModuleLifecycleStore.DesiredState;

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

  private static final class FixtureModule implements ZalavaModule {
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
            public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
              Object endpoint = context.configuration().get("endpoint");
              if (endpoint == null || endpoint.equals("broken")) {
                throw new IllegalStateException("Fixture requires a working endpoint");
              }
              return List.of();
            }
          });
    }
  }

  private static final class ServiceFixtureModule implements ZalavaModule {
    private static final ZalavaServiceContract<AutoCloseable> CONTRACT =
        new ZalavaServiceContract<>("fixture-service", "1", AutoCloseable.class);

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
    public List<ZalavaServiceFactory<?>> serviceFactories() {
      return List.of(
          new ZalavaServiceFactory<AutoCloseable>() {
            @Override
            public ZalavaServiceDescriptor descriptor() {
              return new ZalavaServiceDescriptor("fixture-service", "service-fixture", "1");
            }

            @Override
            public ZalavaServiceContract<AutoCloseable> contract() {
              return CONTRACT;
            }

            @Override
            public AutoCloseable create(org.zalava.api.ZalavaServiceFactoryContext context) {
              SERVICE_FACTORY_CREATIONS.incrementAndGet();
              throw new IllegalStateException("service endpoint is required");
            }
          });
    }
  }
}
