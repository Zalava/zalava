package org.zalava.api;

import java.util.Optional;

/** Host-controlled secret lookup available only to the currently scoped factory. */
@FunctionalInterface
public interface FactorySecretAccess {
  Optional<char[]> resolve(String reference);

  static FactorySecretAccess none() {
    return ignored -> Optional.empty();
  }
}
