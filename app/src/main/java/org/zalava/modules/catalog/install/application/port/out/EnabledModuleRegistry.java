package org.zalava.modules.catalog.install.application.port.out;

import java.util.List;

public interface EnabledModuleRegistry {

  List<ModuleEnablement.EnabledModule> enabledModules();
}
