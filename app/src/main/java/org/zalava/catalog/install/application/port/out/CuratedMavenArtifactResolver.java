package org.zalava.catalog.install.application.port.out;

import java.net.URI;
import org.zalava.catalog.SourceModuleIndex;

public interface CuratedMavenArtifactResolver {

  ResolvedArtifact resolve(Request request);

  void discard(ResolvedArtifact artifact);

  record Request(String repositoryId, URI repositoryUrl, SourceModuleIndex.Artifact artifact) {}

  record ResolvedArtifact(String path, String sha256Digest) {}
}
