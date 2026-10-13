package org.zalava.capabilities.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.capabilities.discovery.adapter.out.http.LocatorModuleCapabilityCatalog;
import org.zalava.capabilities.discovery.application.DefaultModuleSuggestions;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

@Configuration(proxyBeanMethods = false)
public class CatalogSuggestionConfiguration {
  @Bean
  ModuleSuggestions moduleSuggestions(
      ModuleLocatorReleaseLocator locator,
      @Value("${zalava.catalog.suggestions.enabled:true}") boolean enabled) {
    return enabled
        ? new DefaultModuleSuggestions(new LocatorModuleCapabilityCatalog(locator))
        : capability -> ModuleSuggestions.Outcome.of(ModuleSuggestions.Status.DISABLED);
  }
}
