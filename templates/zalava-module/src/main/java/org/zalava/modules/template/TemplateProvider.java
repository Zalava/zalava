package org.zalava.modules.template;

import java.util.List;
import java.util.Map;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;

final class TemplateProvider implements SeaProvider {

  @Override
  public ProviderDescriptor descriptor() {
    return new ProviderDescriptor(
        "template",
        TemplateSeaModule.MODULE_ID,
        "template",
        "Template Provider",
        "Replace this sample provider.",
        TemplateSeaModule.version(),
        ProviderCapabilities.toolsOnly(),
        List.of("template"),
        Map.of());
  }

  @Override
  public ProviderCapabilities capabilities() {
    return descriptor().capabilities();
  }

  @Override
  public List<SeaToolDescriptor> listTools() {
    return List.of();
  }
}
