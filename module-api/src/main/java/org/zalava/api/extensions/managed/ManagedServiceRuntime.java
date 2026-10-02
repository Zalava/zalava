package org.zalava.api.extensions.managed;

import org.zalava.api.ManagedServiceAuthority;
import org.zalava.api.ZalavaServiceContract;

/**
 * SEA-owned host facility for module-declared managed-service lifecycle requests.
 *
 * <p>The runtime resolves the administrator grant from the authority-bound module identity. This
 * contract does not expose an engine or grant modules a host command channel.
 */
public interface ManagedServiceRuntime {

  ZalavaServiceContract<ManagedServiceRuntime> CONTRACT =
      new ZalavaServiceContract<>("managed-service-runtime", "1", ManagedServiceRuntime.class);

  ManagedServiceLifecycleResult request(
      ManagedServiceAuthority authority, ManagedServiceDesiredState desiredState);
}
