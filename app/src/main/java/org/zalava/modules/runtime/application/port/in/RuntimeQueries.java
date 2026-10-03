package org.zalava.modules.runtime.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.modules.runtime.LoadedZalavaProvider;

/** Framework-free queries over the providers instantiated for this runtime. */
public interface RuntimeQueries {

  List<ZalavaModule> modules();

  /** Modules whose providers and services are currently active. */
  default List<ZalavaModule> activeModules() {
    return modules();
  }

  List<LoadedZalavaProvider> loadedProviders();

  default List<ZalavaProvider> providers() {
    return loadedProviders().stream().map(LoadedZalavaProvider::provider).toList();
  }

  default Optional<LoadedZalavaProvider> findLoadedProvider(String providerId) {
    return loadedProviders().stream()
        .filter(provider -> provider.provider().descriptor().providerId().equals(providerId))
        .findFirst();
  }

  default Optional<ZalavaProvider> findProvider(String providerId) {
    return findLoadedProvider(providerId).map(LoadedZalavaProvider::provider);
  }

  /**
   * Resolves one active Zalava-owned typed service without exposing its factory, classloader,
   * module configuration, or any Spring infrastructure.
   */
  default <T> Optional<LoadedZalavaService<T>> findService(ZalavaServiceContract<T> contract) {
    return Optional.empty();
  }

  record LoadedZalavaService<T>(ZalavaServiceDescriptor descriptor, T service) {}
}
