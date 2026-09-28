package org.zalava.modules.template;

import java.util.List;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaProvider;

final class TemplateProviderFactory implements ProviderFactory {

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        "template",
        TemplateSeaModule.MODULE_ID,
        "template",
        "Template Provider",
        "Replace this sample provider factory.");
  }

  @Override
  public List<SeaProvider> createProviders(ProviderFactoryContext context) {
    return List.of(new TemplateProvider());
  }
}
