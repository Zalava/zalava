package org.zalava.modules.runtime;

import java.util.List;
import org.zalava.ProviderFactoryContext;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaServiceContract;
import org.zalava.modules.runtime.application.DefaultRuntimeQueries;

public final class DefaultSeaRuntime implements SeaRuntime {

  private final DefaultRuntimeQueries delegate;

  public DefaultSeaRuntime(
      SeaModuleRegistry moduleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.delegate = new DefaultRuntimeQueries(moduleRegistry, providerFactoryContext);
  }

  @Override
  public List<ZalavaModule> modules() {
    return delegate.modules();
  }

  @Override
  public List<LoadedSeaProvider> loadedProviders() {
    return delegate.loadedProviders();
  }

  @Override
  public <T> java.util.Optional<LoadedSeaService<T>> findService(
      ZalavaServiceContract<T> contract) {
    return delegate.findService(contract);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
