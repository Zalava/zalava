package org.zalava.modules.runtime.application;

import java.util.ArrayList;
import java.util.List;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.modules.runtime.LoadedSeaProvider;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.runtime.application.port.out.RuntimeModuleRegistry;

/** Instantiates and closes provider lifecycles without transport or filesystem concerns. */
public final class DefaultRuntimeQueries implements RuntimeQueries, AutoCloseable {

  private final List<ZalavaModule> modules;
  private final List<LoadedSeaProvider> loadedProviders;
  private final ModuleServiceRuntime services;

  public DefaultRuntimeQueries(
      RuntimeModuleRegistry moduleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.modules = List.copyOf(moduleRegistry.modules());
    this.services = new ModuleServiceRuntime(this.modules, providerFactoryContext);
    try {
      ProviderFactoryContext scopedContext = services.providerContext(providerFactoryContext);
      this.loadedProviders =
          this.modules.stream()
              .flatMap(
                  module ->
                      module.providerFactories().stream()
                          .flatMap(
                              factory ->
                                  factory
                                      .createProviders(
                                          scopedContext.forFactory(
                                              module.descriptor().moduleId(),
                                              factory.descriptor().factoryId()))
                                      .stream()
                                      .map(
                                          provider ->
                                              new LoadedSeaProvider(
                                                  module.descriptor(),
                                                  factory.descriptor(),
                                                  provider))))
              .toList();
    } catch (RuntimeException exception) {
      services.close();
      throw exception;
    }
  }

  @Override
  public List<ZalavaModule> modules() {
    return modules;
  }

  @Override
  public List<LoadedSeaProvider> loadedProviders() {
    return loadedProviders;
  }

  @Override
  public <T> java.util.Optional<LoadedSeaService<T>> findService(
      ZalavaServiceContract<T> contract) {
    return services.findService(contract);
  }

  @Override
  public void close() {
    List<Exception> failures = new ArrayList<>();
    for (int index = loadedProviders.size() - 1; index >= 0; index--) {
      ZalavaProvider provider = loadedProviders.get(index).provider();
      try {
        provider.close();
      } catch (Exception exception) {
        failures.add(exception);
      }
    }
    try {
      services.close();
    } catch (RuntimeException exception) {
      failures.add(exception);
    }
    if (!failures.isEmpty()) {
      IllegalStateException failure =
          new IllegalStateException("Unable to close one or more SEA providers or services");
      failures.forEach(failure::addSuppressed);
      throw failure;
    }
  }
}
