package org.zalava.catalog.install.application.port.in;

import org.zalava.catalog.SourceModuleCatalogSearch;

public interface CuratedMavenModuleInstallation {

  BinaryModuleInstallation.InstalledBinaryModule install(
      SourceModuleCatalogSearch.Result result, String repositoryId);
}
