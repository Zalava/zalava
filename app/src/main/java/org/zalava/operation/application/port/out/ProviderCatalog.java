package org.zalava.operation.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.SeaProvider;

public interface ProviderCatalog {

  Optional<SeaProvider> findProvider(String providerId);

  default List<SeaProvider> providers() {
    return List.of();
  }
}
