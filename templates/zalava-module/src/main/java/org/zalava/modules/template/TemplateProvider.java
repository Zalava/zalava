package org.zalava.modules.template;

import java.util.List;
import java.util.Map;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;

final class TemplateProvider implements ZalavaProvider {

  @Override
  public ProviderDescriptor descriptor() {
    return new ProviderDescriptor(
        "template",
        TemplateZalavaModule.MODULE_ID,
        "template",
        "Template Provider",
        "Replace this sample provider.",
        TemplateZalavaModule.version(),
        ProviderCapabilities.toolsOnly(),
        List.of("template"),
        Map.of());
  }

  @Override
  public ProviderCapabilities capabilities() {
    return descriptor().capabilities();
  }

  @Override
  public List<ZalavaToolDescriptor> listTools() {
    return List.of();
  }
}
