package org.zalava.capabilities.discovery.adapter.out.http;

import java.util.List;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions.Module;
import org.zalava.capabilities.discovery.application.port.out.ModuleCapabilityCatalog;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

public final class LocatorModuleCapabilityCatalog implements ModuleCapabilityCatalog {
  private final ModuleLocatorReleaseLocator locator;

  public LocatorModuleCapabilityCatalog(ModuleLocatorReleaseLocator locator) {
    this.locator = locator;
  }

  @Override
  public List<Module> modules() {
    return locator.modules().stream()
        .limit(100)
        .map(module -> new Module(module.moduleId(), module.displayName(), module.description()))
        .toList();
  }
}
