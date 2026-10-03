package org.zalava.capabilities.discovery;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.zalava.capabilities.discovery.adapter.out.filesystem.FileSystemCapabilityGapEvidenceStore;
import org.zalava.capabilities.discovery.adapter.out.http.JdkModuleLocatorRemoteModuleCatalog;
import org.zalava.capabilities.discovery.application.DefaultCapabilityGapEvidenceQueries;
import org.zalava.capabilities.discovery.application.DefaultRemoteCapabilityDiscovery;
import org.zalava.capabilities.discovery.application.RemoteCandidatePolicy;
import org.zalava.capabilities.discovery.application.port.in.CapabilityGapEvidenceQueries;
import org.zalava.capabilities.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.capabilities.discovery.application.port.out.CapabilityGapEvidenceStore;
import org.zalava.capabilities.discovery.application.port.out.RemoteModuleCatalog;
import org.zalava.modules.catalog.adapter.out.http.JdkModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.adapter.out.http.JdkModuleLocatorReleaseLocator;

@Configuration
public class RemoteDiscoveryConfiguration {

  @Bean
  CapabilityGapEvidenceStore capabilityGapEvidenceStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemCapabilityGapEvidenceStore(workspace.getFilePath());
  }

  @Bean
  CapabilityGapEvidenceQueries capabilityGapEvidenceQueries(CapabilityGapEvidenceStore store) {
    return new DefaultCapabilityGapEvidenceQueries(store);
  }

  @Bean
  RemoteModuleCatalog remoteModuleCatalog(
      @Value("${zalava.discovery.remote.enabled:false}") boolean enabled,
      @Value("${zalava.discovery.remote.module-locator-url:}") String moduleLocatorUrl,
      @Value("${zalava.discovery.remote.github.token:}") String githubToken,
      @Value("${zalava.discovery.remote.max-modules:5}") int maxModules,
      @Value("${zalava.discovery.remote.max-candidates:10}") int maxCandidates) {
    if (!enabled || moduleLocatorUrl.isBlank()) {
      return RemoteModuleCatalog.disabled();
    }
    return new JdkModuleLocatorRemoteModuleCatalog(
        new JdkModuleLocatorReleaseLocator(URI.create(moduleLocatorUrl), githubToken),
        new JdkModuleReleaseIndexRetrieval(githubToken),
        githubToken,
        maxModules,
        maxCandidates);
  }

  @Bean
  RemoteCandidatePolicy remoteCandidatePolicy(
      @Value(
              "${zalava.discovery.remote.blocked-permissions:shell,broad-access,unrestricted-host,"
                  + "host-filesystem-unrestricted,credentials}")
          String blockedPermissions) {
    Set<String> blocked = new LinkedHashSet<>();
    Arrays.stream(blockedPermissions.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
        .forEach(blocked::add);
    return new RemoteCandidatePolicy(blocked);
  }

  @Bean
  RemoteCapabilityDiscovery remoteCapabilityDiscovery(
      RemoteModuleCatalog catalog,
      RemoteCandidatePolicy policy,
      CapabilityGapEvidenceStore evidenceStore,
      @Value("${zalava.discovery.remote.max-candidates:10}") int maxCandidates) {
    return new DefaultRemoteCapabilityDiscovery(
        catalog, policy, evidenceStore, Clock.systemUTC(), maxCandidates);
  }
}
