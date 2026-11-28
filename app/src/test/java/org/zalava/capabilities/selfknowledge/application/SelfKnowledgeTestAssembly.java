package org.zalava.capabilities.selfknowledge.application;

import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.out.ChatProviderSelection;
import org.zalava.capabilities.selfknowledge.application.port.out.CoreVersion;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleConfigurationStateQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleStateQueries;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

/** Composition of the query and its module projection. */
final class SelfKnowledgeTestAssembly {
  private SelfKnowledgeTestAssembly() {}

  static DefaultSelfKnowledgeQueries create(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      RuntimeQueries runtime,
      ProviderCatalog providers,
      MemberToolVisibility access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess,
      ModuleStateQueries moduleStates,
      ModuleQueries installedModules,
      ModuleSuggestions suggestions,
      ChatProviderSelection chatProvider,
      CoreVersion coreVersion,
      ModuleConfigurationStateQueries moduleConfiguration,
      ManagedServiceQueries managedServices) {
    var modules =
        new ModuleCapabilityProjection(
            runtime,
            providers,
            access,
            routes,
            pageAccess,
            moduleStates,
            installedModules,
            moduleConfiguration,
            managedServices);
    return new DefaultSelfKnowledgeQueries(
        actors,
        accounts,
        runtime,
        installedModules,
        suggestions,
        chatProvider,
        coreVersion,
        modules);
  }
}
