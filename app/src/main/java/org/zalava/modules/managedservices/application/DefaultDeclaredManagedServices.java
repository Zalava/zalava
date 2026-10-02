package org.zalava.modules.managedservices.application;

import java.util.List;
import java.util.Objects;
import org.zalava.ManagedServiceDeclaration;
import org.zalava.modules.managedservices.application.port.in.DeclaredManagedServices;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Aggregates module-declared managed services from the loaded runtime. */
public final class DefaultDeclaredManagedServices implements DeclaredManagedServices {

  private final RuntimeQueries runtime;

  public DefaultDeclaredManagedServices(RuntimeQueries runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public List<ManagedServiceDeclaration> forModule(String moduleId) {
    if (moduleId == null || moduleId.isBlank()) {
      return List.of();
    }
    return runtime.modules().stream()
        .filter(module -> moduleId.equals(module.descriptor().moduleId()))
        .flatMap(module -> module.managedServices().stream())
        .toList();
  }
}
