package org.zalava.modules.catalog.install.application;

import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

/** Adapts a catalog selection to the shared release prepare/approval/enablement workflow. */
public final class DefaultModuleLocatorInstallation implements ModuleLocatorInstallation {
  public static final String GITHUB_PACKAGES_ID = "github-packages";
  private final ModuleLocatorReleaseLocator locator;
  private final ModuleReleaseInstallation installation;

  public DefaultModuleLocatorInstallation(
      ModuleLocatorReleaseLocator locator, ModuleReleaseInstallation installation) {
    this.locator = locator;
    this.installation = installation;
  }

  @Override
  public ModuleReleaseInstallRequest create(Request request) {
    ModuleLocatorReleaseLocator.ResolvedModule module = locator.resolve(request.moduleId());
    return installation.create(
        new ModuleReleaseInstallation.Request(
            module.manifestUri(),
            null,
            module.moduleId(),
            request.version(),
            GITHUB_PACKAGES_ID,
            module.artifactRepositoryUri(),
            request.developmentRequestId()));
  }
}
