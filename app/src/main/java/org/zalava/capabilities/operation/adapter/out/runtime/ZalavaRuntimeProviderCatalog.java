package org.zalava.capabilities.operation.adapter.out.runtime;

import java.util.List;
import java.util.Optional;
import org.zalava.api.ZalavaProvider;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

public final class ZalavaRuntimeProviderCatalog implements ProviderCatalog {

  private final RuntimeQueries runtimeQueries;

  public ZalavaRuntimeProviderCatalog(RuntimeQueries runtimeQueries) {
    this.runtimeQueries = runtimeQueries;
  }

  @Override
  public Optional<ZalavaProvider> findProvider(String providerId) {
    return runtimeQueries.findProvider(providerId);
  }

  @Override
  public List<ZalavaProvider> providers() {
    return runtimeQueries.providers();
  }
}
