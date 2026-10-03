package org.zalava.modules.runtime;

import java.util.List;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.modules.runtime.application.DefaultRuntimeQueries;

public final class DefaultZalavaRuntime implements ZalavaRuntime {

  private final DefaultRuntimeQueries delegate;

  public DefaultZalavaRuntime(
      ZalavaModuleRegistry moduleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.delegate = new DefaultRuntimeQueries(moduleRegistry, providerFactoryContext);
  }

  @Override
  public List<ZalavaModule> modules() {
    return delegate.modules();
  }

  @Override
  public List<LoadedZalavaProvider> loadedProviders() {
    return delegate.loadedProviders();
  }

  @Override
  public <T> java.util.Optional<LoadedZalavaService<T>> findService(
      ZalavaServiceContract<T> contract) {
    return delegate.findService(contract);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
