package org.zalava.modules.catalog.install.application;

import org.zalava.modules.catalog.install.application.port.in.EnabledModuleManagement;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;

public final class DefaultEnabledModuleManagement implements EnabledModuleManagement {

  private final ModuleEnablement enablement;

  public DefaultEnabledModuleManagement(ModuleEnablement enablement) {
    this.enablement = enablement;
  }

  @Override
  public DisableOutcome disable(String moduleId) {
    ModuleEnablement.DisableResult result = enablement.disable(moduleId);
    return new EnabledModuleManagement.DisableOutcome(result.moduleId(), result.changed());
  }
}
