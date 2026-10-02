package org.zalava.modules.template;

import java.util.List;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;

public final class TemplateZalavaModule implements ZalavaModule {

  public static final String MODULE_ID = "zalava-module-template";

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        MODULE_ID, version(), "Module Template", "Replace this sample Zalava module description.");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of(new TemplateProviderFactory());
  }

  static String version() {
    return TemplateZalavaModule.class.getPackage().getImplementationVersion();
  }
}
