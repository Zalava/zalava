package org.zalava.managed;

import org.zalava.ManagedServiceAuthority;
import org.zalava.ZalavaServiceContract;

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
