package org.zalava.control.adapter.out.runtime;

import java.util.List;
import java.util.Optional;
import org.zalava.SeaProvider;
import org.zalava.SeaVerificationDescriptor;
import org.zalava.control.application.port.out.RuntimeVerificationCatalog;
import org.zalava.runtime.application.port.in.RuntimeQueries;

public final class SeaRuntimeVerificationCatalog implements RuntimeVerificationCatalog {
  private final RuntimeQueries runtime;

  public SeaRuntimeVerificationCatalog(RuntimeQueries runtime) {
    this.runtime = runtime;
  }

  @Override
  public List<SeaVerificationDescriptor> verifications() {
    return runtime.modules().stream()
        .flatMap(module -> module.verificationContributors().stream())
        .flatMap(contributor -> contributor.verifications().stream())
        .toList();
  }

  @Override
  public Optional<SeaProvider> findProvider(String providerId) {
    return runtime.findProvider(providerId);
  }
}
