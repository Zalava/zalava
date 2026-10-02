package org.zalava.api;

import java.util.List;

public interface ProviderFactory {

  ProviderFactoryDescriptor descriptor();

  List<ZalavaProvider> createProviders(ProviderFactoryContext context);
}
