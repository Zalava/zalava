package org.zalava.capabilities.operation.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.zalava.api.ZalavaProvider;

public interface ProviderCatalog {

  Optional<ZalavaProvider> findProvider(String providerId);

  /** Host-assigned identity for a loaded provider instance, if its provenance is established. */
  default Optional<UUID> generationOf(ZalavaProvider provider) {
    return Optional.empty();
  }

  default List<ZalavaProvider> providers() {
    return List.of();
  }
}
