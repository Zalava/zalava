package org.zalava.modules.managedservices.application.port.in;

import java.util.Optional;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;

/**
 * Turns a module's declared managed services into an aggregate administrator-approvable install
 * request. SEA derives a grant equal to the declared desired state, so approval can only confirm
 * what the module declared, never widen it.
 */
public interface ModuleManagedServiceInstallation {

  /** Plans all declared services of the module; the result is PENDING until approved. */
  ManagedServiceInstallRequest requestDeclaredInstall(String moduleId);

  /** The most recent install request touching this module, if any. */
  Optional<ManagedServiceInstallRequest> latestForModule(String moduleId);

  /**
   * Approves the module's pending install request, executing the declared services. Throws when the
   * module has no pending request.
   */
  ManagedServiceInstallRequest approveLatest(String moduleId);

  /** Denies the module's pending install request. Throws when the module has no pending request. */
  ManagedServiceInstallRequest denyLatest(String moduleId);
}
