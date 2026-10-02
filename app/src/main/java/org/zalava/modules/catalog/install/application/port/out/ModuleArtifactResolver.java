package org.zalava.modules.catalog.install.application.port.out;

import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.install.ModuleArtifactRepository;

/** Resolves a declared artifact through its typed repository protocol. */
public interface ModuleArtifactResolver {
  ResolvedArtifact resolve(
      ModuleArtifactRepository repository, ModuleReleaseIndex.Artifact artifact);

  record ResolvedArtifact(String path, String sha256Digest) {}
}
