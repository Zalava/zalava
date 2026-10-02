package org.zalava.capabilities.operation.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.api.ZalavaProvider;

public interface ProviderCatalog {

  Optional<ZalavaProvider> findProvider(String providerId);

  default List<ZalavaProvider> providers() {
    return List.of();
  }
}
