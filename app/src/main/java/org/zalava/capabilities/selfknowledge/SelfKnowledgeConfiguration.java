package org.zalava.capabilities.selfknowledge;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.assistant.chat.application.port.in.ChatProviderStatus;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.adapter.out.runtime.ZalavaRuntimeProviderCatalog;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.selfknowledge.adapter.out.HostMemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.DefaultSelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

@Configuration(proxyBeanMethods = false)
public class SelfKnowledgeConfiguration {
  @Bean
  public SelfKnowledgeQueries selfKnowledgeQueries(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      ZalavaRuntime runtime,
      MemberToolAccess access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess,
      ModuleQueries installedModules,
      ModuleSuggestions suggestions,
      ChatProviderStatus chatProvider,
      ObjectProvider<BuildProperties> buildProperties) {
    return new DefaultSelfKnowledgeQueries(
        actors,
        accounts,
        runtime,
        new ZalavaRuntimeProviderCatalog(runtime),
        new HostMemberToolVisibility(access),
        routes,
        pageAccess,
        moduleId -> moduleState(runtime, moduleId),
        installedModules,
        suggestions,
        chatProvider::configured,
        () -> {
          BuildProperties build = buildProperties.getIfAvailable();
          return build == null ? "" : build.getVersion();
        });
  }

  private static RuntimeState moduleState(ZalavaRuntime runtime, String moduleId) {
    if (!(runtime instanceof ManagedZalavaRuntime managed)) return RuntimeState.UNKNOWN;
    try {
      return switch (managed.state(moduleId).state()) {
        case RUNNING -> RuntimeState.ACTIVE;
        case STOPPED -> RuntimeState.STOPPED;
        case SETUP_REQUIRED -> RuntimeState.SETUP_REQUIRED;
        case FAILED -> RuntimeState.FAILED;
      };
    } catch (IllegalArgumentException unavailable) {
      return RuntimeState.UNKNOWN;
    }
  }
}
