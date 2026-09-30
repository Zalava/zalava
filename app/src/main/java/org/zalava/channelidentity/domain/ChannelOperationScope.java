package org.zalava.channelidentity.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Explicit operations granted to a linked channel identity. An empty or wildcard scope is invalid.
 */
public record ChannelOperationScope(Set<String> operations) {
  public ChannelOperationScope {
    Objects.requireNonNull(operations, "operations is required");
    operations = Set.copyOf(operations);
    if (operations.isEmpty()
        || operations.size() > 32
        || operations.stream().anyMatch(operation -> !isOperation(operation))) {
      throw new IllegalArgumentException(
          "Operations must contain between 1 and 32 explicit operation identifiers");
    }
  }

  public static ChannelOperationScope of(String... operations) {
    return new ChannelOperationScope(Set.of(operations));
  }

  public boolean allows(String operation) {
    return operations.contains(operation);
  }

  private static boolean isOperation(String operation) {
    return operation != null && operation.matches("[a-z][a-z0-9-]{0,63}:[a-z][a-z0-9-]{0,63}");
  }
}
