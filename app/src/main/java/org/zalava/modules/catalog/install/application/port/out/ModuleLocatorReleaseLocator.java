package org.zalava.modules.catalog.install.application.port.out;

import java.net.URI;
import java.util.List;

/** Resolves a public locator selection to a commit-pinned, module-owned release index. */
public interface ModuleLocatorReleaseLocator {
  default List<Module> modules() {
    return List.of();
  }

  ResolvedModule resolve(String moduleId);

  record Module(String moduleId, String displayName, String description) {}

  record ResolvedModule(
      String moduleId,
      String displayName,
      String description,
      URI manifestUri,
      URI artifactRepositoryUri) {}
}
