package org.zalava.api.extensions.channels;

import java.util.Map;
import java.util.Objects;
import org.zalava.api.FactorySecretAccess;

/**
 * Host-supplied, module-scoped configuration for a channel transport lifecycle.
 *
 * <p>The host owns persistence and secret storage. A channel receives only an immutable
 * configuration document and a resolver scoped to its own module; it must not retain or log
 * resolved secret values.
 */
public record ChannelTransportContext(
    Map<String, Object> configuration, FactorySecretAccess secrets) {
  public ChannelTransportContext {
    configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
    secrets = Objects.requireNonNullElse(secrets, FactorySecretAccess.none());
  }

  public static ChannelTransportContext empty() {
    return new ChannelTransportContext(Map.of(), FactorySecretAccess.none());
  }
}
