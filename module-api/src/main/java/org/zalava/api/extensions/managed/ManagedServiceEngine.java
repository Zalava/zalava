package org.zalava.api.extensions.managed;

import java.util.List;
import org.zalava.api.ZalavaServiceContract;

/**
 * Zalava-owned typed service contract for one OCI engine implementation provided by a module.
 *
 * <p>Zalava owns the engine endpoint and socket authority; a module engine receives only
 * digest-pinned desired state that has already been validated against an administrator-approved
 * grant. Implementations must operate only on resources they created under their own ownership
 * namespace and must never attach to, restart, or inherit foreign host resources.
 *
 * <p>Contract version 2 extends version 1 with {@link #remove(String)}: promoting a new digest
 * candidate or rolling back requires replacing the resource under the deterministic service-id
 * name, which is impossible without a removal primitive. The service id stays stable so existing v1
 * registrations resolve the same contract instance; Zalava treats the contract as v2 from this
 * release on and every engine module must implement the full surface.
 */
public interface ManagedServiceEngine {

  ZalavaServiceContract<ManagedServiceEngine> CONTRACT =
      new ZalavaServiceContract<>("managed-service-engine", "2", ManagedServiceEngine.class);

  /** Observes the current engine state of one Zalava-owned managed service without mutating it. */
  Observation inspect(String serviceId);

  /**
   * Acquires and provisions the digest-pinned artifact for one service record. Implementations
   * verify the artifact digest during acquisition and stamp ownership metadata on everything they
   * create.
   */
  void create(Request request);

  /** Starts the owned, created service. Never addresses foreign resources. */
  void start(String serviceId);

  /** Stops the owned, running service. Never addresses foreign resources. */
  void stop(String serviceId);

  /**
   * Removes the owned, existing service resource so a later {@link #create(Request)} can re-create
   * it under the same deterministic name. Never addresses foreign resources; callers stop the
   * service first.
   */
  void remove(String serviceId);

  /** Bounded recent engine log lines; diagnostic, best-effort, engine-dependent retention. */
  List<String> recentLogs(String serviceId, int maximumLines);

  /** Engine-independent observation of one managed service. */
  record Observation(
      boolean exists, boolean running, boolean ready, String ownerModuleId, String dataIdentity) {}

  /**
   * One deterministic create request: the reconciled service identity, its validated desired state,
   * the approved grant it was validated against, and the Zalava-owned data identity stamped on
   * every created resource.
   */
  record Request(
      String serviceId,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant,
      String ownedDataIdentity) {

    public Request {
      ManagedServiceDesiredState.requireId(serviceId, "serviceId");
      if (desiredState == null) {
        throw new IllegalArgumentException("desiredState must not be null");
      }
      if (grant == null) {
        throw new IllegalArgumentException("grant must not be null");
      }
      if (ownedDataIdentity == null || ownedDataIdentity.isBlank()) {
        throw new IllegalArgumentException("ownedDataIdentity must not be blank");
      }
    }
  }
}
