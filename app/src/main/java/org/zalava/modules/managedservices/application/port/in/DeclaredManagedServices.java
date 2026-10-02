package org.zalava.modules.managedservices.application.port.in;

import java.util.List;
import org.zalava.ManagedServiceDeclaration;

/**
 * Reads the managed services loaded modules declare through {@link
 * org.zalava.ZalavaModule#managedServices()}. SEA owns validation, grant derivation and
 * installation; modules only declare their desired state.
 */
public interface DeclaredManagedServices {

  /** Declarations of the given module, in module order; never null. */
  List<ManagedServiceDeclaration> forModule(String moduleId);
}
