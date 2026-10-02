package org.zalava.web.control.adapter.out.runtime;

import java.util.List;
import java.util.Optional;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaVerificationDescriptor;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.web.control.application.port.out.RuntimeVerificationCatalog;

public final class SeaRuntimeVerificationCatalog implements RuntimeVerificationCatalog {
  private final RuntimeQueries runtime;

  public SeaRuntimeVerificationCatalog(RuntimeQueries runtime) {
    this.runtime = runtime;
  }

  @Override
  public List<ZalavaVerificationDescriptor> verifications() {
    return runtime.modules().stream()
        .flatMap(module -> module.verificationContributors().stream())
        .flatMap(contributor -> contributor.verifications().stream())
        .toList();
  }

  @Override
  public Optional<ZalavaProvider> findProvider(String providerId) {
    return runtime.findProvider(providerId);
  }
}
