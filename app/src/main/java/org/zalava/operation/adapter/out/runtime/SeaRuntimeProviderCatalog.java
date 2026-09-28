package org.zalava.operation.adapter.out.runtime;

import java.util.List;
import java.util.Optional;
import org.zalava.SeaProvider;
import org.zalava.operation.application.port.out.ProviderCatalog;
import org.zalava.runtime.application.port.in.RuntimeQueries;

public final class SeaRuntimeProviderCatalog implements ProviderCatalog {

  private final RuntimeQueries runtimeQueries;

  public SeaRuntimeProviderCatalog(RuntimeQueries runtimeQueries) {
    this.runtimeQueries = runtimeQueries;
  }

  @Override
  public Optional<SeaProvider> findProvider(String providerId) {
    return runtimeQueries.findProvider(providerId);
  }

  @Override
  public List<SeaProvider> providers() {
    return runtimeQueries.providers();
  }
}
