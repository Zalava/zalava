package org.zalava.runtime;

import java.util.List;
import org.zalava.ProviderFactoryContext;
import org.zalava.SeaModule;
import org.zalava.SeaServiceContract;
import org.zalava.runtime.application.DefaultRuntimeQueries;

public final class DefaultSeaRuntime implements SeaRuntime {

  private final DefaultRuntimeQueries delegate;

  public DefaultSeaRuntime(
      SeaModuleRegistry moduleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.delegate = new DefaultRuntimeQueries(moduleRegistry, providerFactoryContext);
  }

  @Override
  public List<SeaModule> modules() {
    return delegate.modules();
  }

  @Override
  public List<LoadedSeaProvider> loadedProviders() {
    return delegate.loadedProviders();
  }

  @Override
  public <T> java.util.Optional<LoadedSeaService<T>> findService(SeaServiceContract<T> contract) {
    return delegate.findService(contract);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
