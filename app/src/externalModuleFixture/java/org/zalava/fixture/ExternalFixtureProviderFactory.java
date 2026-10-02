package org.zalava.fixture;

import java.util.List;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

final class ExternalFixtureProviderFactory implements ProviderFactory {

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        "external-fixture-factory",
        "sea-external-module-fixture",
        "external-fixture",
        "External fixture factory",
        "Creates the external fixture provider");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    return List.of(new ExternalFixtureZalavaProvider());
  }
}
