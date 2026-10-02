package org.zalava.modules.runtime;

import java.io.IOException;
import java.util.List;
import org.zalava.ProviderFactoryContext;
import org.zalava.ZalavaModule;
import org.zalava.modules.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.runtime.adapter.out.classloading.ExternalModuleClassLoader;

/** Compatibility facade for the external-module loading port. */
public final class ExternalSeaModuleLoader
    implements org.zalava.modules.runtime.application.port.in.ExternalModuleLoading {

  private final ExternalModuleClassLoader delegate;

  public ExternalSeaModuleLoader(EnabledModuleRegistry enabledModuleRegistry) {
    this(enabledModuleRegistry, ProviderFactoryContext.empty());
  }

  public ExternalSeaModuleLoader(
      EnabledModuleRegistry enabledModuleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.delegate = new ExternalModuleClassLoader(enabledModuleRegistry, providerFactoryContext);
  }

  @Override
  public List<ZalavaModule> loadModules() {
    return delegate.loadModules();
  }

  /** Retained for runtime-package characterization tests during the compatibility period. */
  void validateLoadedModules(
      List<ModuleEnablement.EnabledModule> enabledModules, List<ZalavaModule> loaded) {
    delegate.validateLoadedModules(enabledModules, loaded);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
