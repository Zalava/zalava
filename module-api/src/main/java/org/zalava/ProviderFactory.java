package org.zalava;

import java.util.List;

public interface ProviderFactory {

  ProviderFactoryDescriptor descriptor();

  List<ZalavaProvider> createProviders(ProviderFactoryContext context);
}
