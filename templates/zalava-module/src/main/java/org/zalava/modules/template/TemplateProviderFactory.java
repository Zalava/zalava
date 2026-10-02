package org.zalava.modules.template;

import java.util.List;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

final class TemplateProviderFactory implements ProviderFactory {

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        "template",
        TemplateZalavaModule.MODULE_ID,
        "template",
        "Template Provider",
        "Replace this sample provider factory.");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    return List.of(new TemplateProvider());
  }
}
