package org.zalava.modules.catalog.install;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.zalava.modules.catalog.adapter.out.http.JdkModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.application.DefaultCatalogQueries;
import org.zalava.modules.catalog.application.port.in.CatalogQueries;
import org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.adapter.out.filesystem.DefaultUploadedModuleInstallation;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemBinaryArtifactInstallation;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInspection;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInstallRequestStore;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemLocalDevelopmentProjectArtifactLocator;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemLocalModuleProjectReleaseLocator;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleReleaseInstallRequestStore;
import org.zalava.modules.catalog.install.adapter.out.http.JdkCuratedMavenArtifactResolver;
import org.zalava.modules.catalog.install.adapter.out.http.JdkModuleLocatorReleaseLocator;
import org.zalava.modules.catalog.install.application.AdministratorAuthorizedInstallations;
import org.zalava.modules.catalog.install.application.ControlCatalogDiscovery;
import org.zalava.modules.catalog.install.application.DefaultBinaryModuleInstallation;
import org.zalava.modules.catalog.install.application.DefaultCuratedMavenModuleInstallation;
import org.zalava.modules.catalog.install.application.DefaultEnabledModuleManagement;
import org.zalava.modules.catalog.install.application.DefaultLocalArtifactModuleInstallation;
import org.zalava.modules.catalog.install.application.DefaultLocalDevelopmentProjectInstallation;
import org.zalava.modules.catalog.install.application.DefaultLocalModuleProjectInstallation;
import org.zalava.modules.catalog.install.application.DefaultModuleLocatorInstallation;
import org.zalava.modules.catalog.install.application.DefaultModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.CuratedMavenModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.EnabledModuleManagement;
import org.zalava.modules.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.modules.catalog.install.application.port.in.LocalModuleProjectInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.in.UploadedModuleInstallation;
import org.zalava.modules.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInstallRequestStore;
import org.zalava.modules.catalog.install.application.port.out.LocalModuleProjectReleaseLocator;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.modules.catalog.install.application.port.out.ModuleReleaseInstallRequestStore;
import org.zalava.modules.development.adapter.out.filesystem.FileSystemInstalledModuleAcceptanceStore;
import org.zalava.modules.development.application.DevelopmentCandidateValidationGateway;
import org.zalava.modules.development.application.port.out.InstalledModuleAcceptanceStore;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemManagedZalavaRestart;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.modules.runtime.application.AdministratorAuthorizedManagedZalavaRestart;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;
import org.zalava.web.control.application.AdministratorControlAuthorization;

@Configuration
public class SourceModuleInstallationConfiguration {
  @Bean
  FileSystemModuleLifecycleStore fileSystemModuleLifecycleStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemModuleLifecycleStore(workspace.getFilePath());
  }

  @Bean
  ManagedZalavaRestart rawManagedZalavaRestart(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      @Value("${zalava.managed-restart.dispatcher-enabled:false}") boolean dispatcherEnabled)
      throws IOException {
    return new FileSystemManagedZalavaRestart(
        workspace.getFilePath(), Clock.systemUTC(), dispatcherEnabled);
  }

  @Bean
  @Primary
  ManagedZalavaRestart managedZalavaRestart(
      @Qualifier("rawManagedZalavaRestart") ManagedZalavaRestart delegate,
      AdministratorControlAuthorization authorization) {
    return new AdministratorAuthorizedManagedZalavaRestart(delegate, authorization);
  }

  @Bean
  FileSystemModuleEnablement fileSystemModuleEnablement(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemModuleEnablement(workspace.getFilePath());
  }

  @Bean
  EnabledModuleManagement rawEnabledModuleManagement(ModuleEnablement moduleEnablement) {
    return new DefaultEnabledModuleManagement(moduleEnablement);
  }

  @Bean
  @Primary
  EnabledModuleManagement enabledModuleManagement(
      @Qualifier("rawEnabledModuleManagement") EnabledModuleManagement delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.enablement(delegate, authorization);
  }

  @Bean
  BinaryModuleInstallation binaryModuleInstallation(
      ModuleEnablement moduleEnablement, @Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    return new DefaultBinaryModuleInstallation(
        new FileSystemBinaryArtifactInstallation(workspace.getFilePath()), moduleEnablement);
  }

  @Bean
  CuratedMavenArtifactResolver curatedMavenArtifactResolver(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      @Value("${zalava.catalog.github-packages.username:}") String githubPackagesUsername,
      @Value("${zalava.catalog.github-packages.token:}") String githubPackagesToken)
      throws IOException {
    if (githubPackagesUsername.isBlank() && githubPackagesToken.isBlank())
      return new JdkCuratedMavenArtifactResolver(workspace.getFilePath());
    return new JdkCuratedMavenArtifactResolver(
        workspace.getFilePath(),
        java.util.Map.of(
            "github-packages",
            new JdkCuratedMavenArtifactResolver.Credentials(
                githubPackagesUsername, githubPackagesToken)));
  }

  @Bean
  ModuleReleaseIndexRetrieval moduleReleaseIndexRetrieval(
      @Value("${zalava.catalog.github.token:}") String githubToken) {
    return new JdkModuleReleaseIndexRetrieval(githubToken);
  }

  @Bean
  CatalogQueries catalogQueries() {
    return new DefaultCatalogQueries();
  }

  @Bean
  ControlCatalogDiscovery controlCatalogDiscovery(
      ModuleLocatorReleaseLocator locator,
      ModuleReleaseIndexRetrieval releaseIndexes,
      @Value("${zalava.catalog.github.token:}") String githubToken) {
    return new ControlCatalogDiscovery(locator, releaseIndexes, githubToken);
  }

  @Bean
  ModuleReleaseInstallRequestStore moduleReleaseInstallRequestStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemModuleReleaseInstallRequestStore(workspace.getFilePath());
  }

  @Bean
  ModuleReleaseInstallation rawModuleReleaseInstallation(
      ModuleReleaseIndexRetrieval manifests,
      CatalogQueries catalog,
      CuratedMavenArtifactResolver artifacts,
      ModuleReleaseInstallRequestStore requests,
      BinaryModuleInstallation installation,
      DevelopmentCandidateValidationGateway validationGateway) {
    return new DefaultModuleReleaseInstallation(
        manifests,
        catalog,
        artifacts,
        new ModuleReleaseBinaryInstallRequestFactory(),
        requests,
        installation,
        validationGateway,
        Clock.systemUTC());
  }

  @Bean
  @Primary
  ModuleReleaseInstallation moduleReleaseInstallation(
      @Qualifier("rawModuleReleaseInstallation") ModuleReleaseInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.releases(delegate, authorization);
  }

  @Bean
  ModuleLocatorReleaseLocator moduleLocatorReleaseLocator(
      @Value(
              "${zalava.catalog.module-locator.url:https://raw.githubusercontent.com/Zalava/zalava-catalog/main/catalog.yaml}")
          String catalogUrl,
      @Value("${zalava.catalog.github.token:}") String githubToken) {
    return new JdkModuleLocatorReleaseLocator(URI.create(catalogUrl), githubToken);
  }

  @Bean
  ModuleLocatorInstallation rawModuleLocatorInstallation(
      ModuleLocatorReleaseLocator locator,
      @Qualifier("rawModuleReleaseInstallation") ModuleReleaseInstallation installation) {
    return new DefaultModuleLocatorInstallation(locator, installation);
  }

  @Bean
  @Primary
  ModuleLocatorInstallation moduleLocatorInstallation(
      @Qualifier("rawModuleLocatorInstallation") ModuleLocatorInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.locator(delegate, authorization);
  }

  @Bean
  CuratedMavenModuleInstallation curatedMavenModuleInstallation(
      CuratedMavenArtifactResolver artifacts, BinaryModuleInstallation installation) {
    return new DefaultCuratedMavenModuleInstallation(artifacts, installation);
  }

  @Bean
  LocalArtifactInstallRequestStore localArtifactInstallRequestStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemLocalArtifactInstallRequestStore(workspace.getFilePath());
  }

  @Bean
  UploadedModuleInstallation rawUploadedModuleInstallation(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      ModuleReleaseInstallRequestStore requests)
      throws IOException {
    return new DefaultUploadedModuleInstallation(
        workspace.getFilePath(), requests, Clock.systemUTC());
  }

  @Bean
  @Primary
  UploadedModuleInstallation uploadedModuleInstallation(
      @Qualifier("rawUploadedModuleInstallation") UploadedModuleInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.uploaded(delegate, authorization);
  }

  @Bean
  LocalArtifactInspection localArtifactInspection(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      @Value("${agent.modules.local-artifact-roots:}") String additionalRoots)
      throws IOException {
    List<Path> roots = new ArrayList<>(List.of(workspace.getFilePath()));
    for (String root : additionalRoots.split(","))
      if (!root.isBlank()) roots.add(Path.of(root.strip()));
    return new FileSystemLocalArtifactInspection(roots);
  }

  @Bean
  LocalModuleProjectReleaseLocator localModuleProjectReleaseLocator(
      LocalArtifactInspection artifacts) {
    return new FileSystemLocalModuleProjectReleaseLocator(artifacts);
  }

  @Bean
  InstalledModuleAcceptanceStore installedModuleAcceptanceStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemInstalledModuleAcceptanceStore(workspace.getFilePath());
  }

  @Bean
  LocalArtifactModuleInstallation rawLocalArtifactModuleInstallation(
      LocalArtifactInstallRequestStore requests,
      BinaryModuleInstallation installation,
      LocalArtifactInspection artifacts,
      DevelopmentCandidateValidationGateway validationGateway,
      InstalledModuleAcceptanceStore acceptances) {
    return new DefaultLocalArtifactModuleInstallation(
        requests, installation, artifacts, validationGateway, acceptances, Clock.systemUTC());
  }

  @Bean
  @Primary
  LocalArtifactModuleInstallation localArtifactModuleInstallation(
      @Qualifier("rawLocalArtifactModuleInstallation") LocalArtifactModuleInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.localArtifacts(delegate, authorization);
  }

  @Bean
  LocalModuleProjectInstallation rawLocalModuleProjectInstallation(
      LocalModuleProjectReleaseLocator locator,
      @Qualifier("rawLocalArtifactModuleInstallation")
          LocalArtifactModuleInstallation installation) {
    return new DefaultLocalModuleProjectInstallation(locator, installation);
  }

  @Bean
  @Primary
  LocalModuleProjectInstallation localModuleProjectInstallation(
      @Qualifier("rawLocalModuleProjectInstallation") LocalModuleProjectInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedInstallations.localProjects(delegate, authorization);
  }

  @Bean
  LocalDevelopmentProjectInstallation localDevelopmentProjectInstallation(
      LocalArtifactModuleInstallation installations, LocalArtifactInspection artifacts) {
    return new DefaultLocalDevelopmentProjectInstallation(
        installations, new FileSystemLocalDevelopmentProjectArtifactLocator(artifacts));
  }
}
