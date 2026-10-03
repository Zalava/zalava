package org.zalava.modules.catalog.install.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.in.BinaryModuleInstallation;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;

public final class DefaultBinaryModuleInstallation implements BinaryModuleInstallation {

  private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");

  private final BinaryArtifactInstallation artifacts;
  private final ModuleEnablement modules;

  public DefaultBinaryModuleInstallation(
      BinaryArtifactInstallation artifacts, ModuleEnablement modules) {
    this.artifacts = artifacts;
    this.modules = modules;
  }

  @Override
  public synchronized InstalledBinaryModule install(BinaryModuleInstallRequest request) {
    validate(request);
    List<BinaryArtifactInstallation.Install> bundle = new ArrayList<>();
    bundle.add(
        new BinaryArtifactInstallation.Install(
            request.module().moduleId(),
            request.module().artifact(),
            request.artifactPath(),
            request.artifactDigest()));
    request
        .runtimeArtifacts()
        .forEach(runtime -> bundle.add(toInstall(request.module().moduleId(), runtime)));
    BinaryArtifactInstallation.InstalledBundle installedBundle =
        request.artifactBundle()
            ? artifacts.installBundle(bundle.getFirst())
            : request.runtimeArtifacts().isEmpty()
                ? new BinaryArtifactInstallation.InstalledBundle(
                    List.of(artifacts.install(bundle.getFirst())))
                : artifacts.install(new BinaryArtifactInstallation.BundleInstall(bundle));
    BinaryArtifactInstallation.InstalledArtifact installed = installedBundle.artifacts().getFirst();
    SourceModuleIndex.Source source = request.module().source();
    try {
      ModuleEnablement.EnablementResult enablement =
          modules.enable(
              new ModuleEnablement.EnabledModule(
                  request.module().moduleId(),
                  request.module().version(),
                  installed.path(),
                  installed.digest(),
                  request.module().compatibility().zalavaRuntime(),
                  source == null ? null : source.repository().toString(),
                  source == null ? null : source.license(),
                  request.repositoryId(),
                  request.module().security().permissions(),
                  installedBundle.artifacts().stream()
                      .skip(1)
                      .map(
                          runtime ->
                              new ModuleEnablement.RuntimeArtifact(
                                  runtime.path(), runtime.digest()))
                      .toList()));
      return new InstalledBinaryModule(
          request.module().moduleId(),
          request.module().version(),
          request.repositoryId(),
          installed.path(),
          installed.digest(),
          enablement.registryPath());
    } catch (RuntimeException exception) {
      if (request.artifactBundle() || !request.runtimeArtifacts().isEmpty()) {
        artifacts.discard(installedBundle);
      }
      throw exception;
    }
  }

  private static BinaryArtifactInstallation.Install toInstall(
      String moduleId, BinaryModuleInstallRequest.RuntimeArtifact runtime) {
    return new BinaryArtifactInstallation.Install(
        moduleId, runtime.artifact(), runtime.artifactPath(), runtime.artifactDigest());
  }

  private static void validate(BinaryModuleInstallRequest request) {
    if (request == null) {
      throw new SourceModuleInstallationException("Binary module install request is required");
    }
    SourceModuleIndex.Module module = request.module();
    if (module == null) {
      throw new SourceModuleInstallationException("Binary module metadata is required");
    }
    requireText(module.moduleId(), "module id");
    requireText(module.version(), "module version");
    if (module.artifact() == null) {
      throw new SourceModuleInstallationException(
          "Binary module artifact coordinates are required");
    }
    requireText(module.artifact().groupId(), "artifact group id");
    requireText(module.artifact().artifactId(), "artifact id");
    requireText(module.artifact().version(), "artifact version");
    if (module.source() == null) {
      if (!isLocalProvenance(request.repositoryId())) {
        throw new SourceModuleInstallationException("Binary module source metadata is required");
      }
    } else {
      requireHttps(module.source().repository(), "source repository");
      requireText(module.source().license(), "source license");
    }
    if (module.compatibility() == null
        || module.compatibility().zalavaRuntime() == null
        || module.compatibility().zalavaRuntime().isBlank()) {
      throw new SourceModuleInstallationException(
          "Binary module Zalava runtime compatibility is required");
    }
    if (module.security() == null || module.security().permissions() == null) {
      throw new SourceModuleInstallationException(
          "Binary module declared permissions are required");
    }
    if (request.artifactPath() == null) {
      throw new SourceModuleInstallationException("Binary module artifact path is required");
    }
    requirePattern(request.artifactDigest(), SHA_256, "artifact digest must be a SHA-256 digest");
    for (BinaryModuleInstallRequest.RuntimeArtifact runtime : request.runtimeArtifacts()) {
      if (runtime == null || runtime.artifact() == null) {
        throw new SourceModuleInstallationException("Binary runtime artifact metadata is required");
      }
      requireText(runtime.artifact().groupId(), "runtime artifact group id");
      requireText(runtime.artifact().artifactId(), "runtime artifact id");
      requireText(runtime.artifact().version(), "runtime artifact version");
      requireText(runtime.artifactPath(), "runtime artifact path");
      requirePattern(
          runtime.artifactDigest(), SHA_256, "runtime artifact digest must be a SHA-256 digest");
    }
    requireText(request.repositoryId(), "binary repository id");
  }

  private static boolean isLocalProvenance(String repositoryId) {
    return "local-private".equals(repositoryId) || "local-upload".equals(repositoryId);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new SourceModuleInstallationException("Binary module " + field + " is required");
    }
  }

  private static void requireHttps(URI uri, String field) {
    if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
      throw new SourceModuleInstallationException("Binary module " + field + " must use HTTPS");
    }
  }

  private static void requirePattern(String value, Pattern pattern, String message) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new SourceModuleInstallationException("Binary module " + message);
    }
  }
}
