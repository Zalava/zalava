package org.zalava.catalog.install.application;

import java.util.List;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.application.port.in.EnabledModuleManagement;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.in.LocalModuleProjectInstallation;
import org.zalava.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.catalog.install.application.port.in.UploadedModuleInstallation;
import org.zalava.control.application.AdministratorControlAuthorization;
import org.zalava.development.DevelopmentRequestId;

/** Application-port decorators that require an administrator for instance-wide module lifecycle. */
public final class AdministratorAuthorizedInstallations {
  private AdministratorAuthorizedInstallations() {}

  public static LocalArtifactModuleInstallation localArtifacts(
      LocalArtifactModuleInstallation delegate, AdministratorControlAuthorization authorization) {
    return new LocalArtifactModuleInstallation() {
      @Override
      public LocalArtifactInstallRequest create(
          SourceModuleIndex.Module module, String artifactPath, DevelopmentRequestId requestId) {
        return authorization.call(
            "module-local-artifact:" + module.moduleId(),
            () -> delegate.create(module, artifactPath, requestId));
      }

      @Override
      public LocalArtifactInstallRequest get(String requestId) {
        return authorization.call(
            "module-local-artifact:" + requestId, () -> delegate.get(requestId));
      }

      @Override
      public List<LocalArtifactInstallRequest> recent(int limit) {
        return authorization.call("module-local-artifact:recent", () -> delegate.recent(limit));
      }

      @Override
      public LocalArtifactInstallRequest allow(String requestId) {
        return authorization.call(
            "module-local-artifact-allow:" + requestId, () -> delegate.allow(requestId));
      }

      @Override
      public LocalArtifactInstallRequest deny(String requestId) {
        return authorization.call(
            "module-local-artifact-deny:" + requestId, () -> delegate.deny(requestId));
      }
    };
  }

  public static ModuleReleaseInstallation releases(
      ModuleReleaseInstallation delegate, AdministratorControlAuthorization authorization) {
    return new ModuleReleaseInstallation() {
      @Override
      public ModuleReleaseInstallRequest create(Request request) {
        return authorization.call(
            "module-release:" + request.moduleId(), () -> delegate.create(request));
      }

      @Override
      public ModuleReleaseInstallRequest get(String requestId) {
        return authorization.call("module-release:" + requestId, () -> delegate.get(requestId));
      }

      @Override
      public List<ModuleReleaseInstallRequest> recent(int limit) {
        return authorization.call("module-release:recent", () -> delegate.recent(limit));
      }

      @Override
      public ModuleReleaseInstallRequest allow(String requestId) {
        return authorization.call(
            "module-release-allow:" + requestId, () -> delegate.allow(requestId));
      }

      @Override
      public ModuleReleaseInstallRequest deny(String requestId) {
        return authorization.call(
            "module-release-deny:" + requestId, () -> delegate.deny(requestId));
      }
    };
  }

  public static ModuleLocatorInstallation locator(
      ModuleLocatorInstallation delegate, AdministratorControlAuthorization authorization) {
    return request ->
        authorization.call("module-locator:" + request.moduleId(), () -> delegate.create(request));
  }

  public static UploadedModuleInstallation uploaded(
      UploadedModuleInstallation delegate, AdministratorControlAuthorization authorization) {
    return new UploadedModuleInstallation() {
      @Override
      public ModuleReleaseInstallRequest create(Request request) {
        return authorization.call("module-upload", () -> delegate.create(request));
      }
    };
  }

  public static LocalModuleProjectInstallation localProjects(
      LocalModuleProjectInstallation delegate, AdministratorControlAuthorization authorization) {
    return request ->
        authorization.call(
            "module-local-project:" + request.moduleId(), () -> delegate.create(request));
  }

  public static EnabledModuleManagement enablement(
      EnabledModuleManagement delegate, AdministratorControlAuthorization authorization) {
    return moduleId ->
        authorization.call(
            "module-enablement-disable:" + moduleId, () -> delegate.disable(moduleId));
  }
}
