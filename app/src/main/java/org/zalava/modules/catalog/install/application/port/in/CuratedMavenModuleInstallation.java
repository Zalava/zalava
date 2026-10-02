package org.zalava.modules.catalog.install.application.port.in;

import org.zalava.modules.catalog.SourceModuleCatalogSearch;

public interface CuratedMavenModuleInstallation {

  BinaryModuleInstallation.InstalledBinaryModule install(
      SourceModuleCatalogSearch.Result result, String repositoryId);
}
