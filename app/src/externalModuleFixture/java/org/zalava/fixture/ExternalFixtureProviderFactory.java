package org.zalava.fixture;

import java.util.List;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaProvider;

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
