package org.zalava.catalog.install.application;

import java.util.List;
import org.zalava.catalog.install.application.port.in.ModuleQueries;
import org.zalava.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.catalog.install.application.port.out.ModuleEnablement;

public final class DefaultModuleQueries implements ModuleQueries {

  private final EnabledModuleRegistry enabledModuleRegistry;

  public DefaultModuleQueries(EnabledModuleRegistry enabledModuleRegistry) {
    this.enabledModuleRegistry = enabledModuleRegistry;
  }

  @Override
  public List<EnabledModule> enabledModules() {
    return enabledModuleRegistry.enabledModules().stream()
        .map(DefaultModuleQueries::toEnabledModule)
        .toList();
  }

  private static EnabledModule toEnabledModule(ModuleEnablement.EnabledModule module) {
    return new EnabledModule(
        module.moduleId(),
        module.version(),
        module.sourceRepository(),
        module.binaryRepositoryId(),
        module.declaredPermissions());
  }
}
