package org.zalava.modules.web.adapter.out.runtime;

import java.util.List;
import org.zalava.ZalavaModule;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.port.out.WebExtensionModuleCatalog;

public final class SeaRuntimeWebExtensionModuleCatalog implements WebExtensionModuleCatalog {

  private final RuntimeQueries runtimeQueries;

  public SeaRuntimeWebExtensionModuleCatalog(RuntimeQueries runtimeQueries) {
    this.runtimeQueries = runtimeQueries;
  }

  @Override
  public List<ZalavaModule> modules() {
    return runtimeQueries.activeModules();
  }
}
