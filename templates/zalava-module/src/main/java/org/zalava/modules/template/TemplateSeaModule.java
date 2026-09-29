package org.zalava.modules.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ZalavaModule;

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
    Properties properties = new Properties();
    try (InputStream input = TemplateZalavaModule.class.getResourceAsStream("/module.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing module version metadata");
      }
      properties.load(input);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read module version metadata", exception);
    }
    return properties.getProperty("module.version");
  }
}
