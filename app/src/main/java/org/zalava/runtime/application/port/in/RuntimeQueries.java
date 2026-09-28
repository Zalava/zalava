package org.zalava.runtime.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.SeaModule;
import org.zalava.SeaProvider;
import org.zalava.SeaServiceContract;
import org.zalava.SeaServiceDescriptor;
import org.zalava.runtime.LoadedSeaProvider;

/** Framework-free queries over the providers instantiated for this runtime. */
public interface RuntimeQueries {

  List<SeaModule> modules();

  /** Modules whose providers and services are currently active. */
  default List<SeaModule> activeModules() {
    return modules();
  }

  List<LoadedSeaProvider> loadedProviders();

  default List<SeaProvider> providers() {
    return loadedProviders().stream().map(LoadedSeaProvider::provider).toList();
  }

  default Optional<LoadedSeaProvider> findLoadedProvider(String providerId) {
    return loadedProviders().stream()
        .filter(provider -> provider.provider().descriptor().providerId().equals(providerId))
        .findFirst();
  }

  default Optional<SeaProvider> findProvider(String providerId) {
    return findLoadedProvider(providerId).map(LoadedSeaProvider::provider);
  }

  /**
   * Resolves one active SEA-owned typed service without exposing its factory, classloader, module
   * configuration, or any Spring infrastructure.
   */
  default <T> Optional<LoadedSeaService<T>> findService(SeaServiceContract<T> contract) {
    return Optional.empty();
  }

  record LoadedSeaService<T>(SeaServiceDescriptor descriptor, T service) {}
}
