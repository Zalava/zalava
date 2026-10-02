package org.zalava.modules.catalog.install.application.port.out;

import org.zalava.modules.catalog.SourceModuleIndex;

/** Locates a trusted built artifact and its metadata from a local development project. */
public interface LocalDevelopmentProjectArtifactLocator {

  ResolvedProjectArtifact resolve(String projectDirectory, String moduleId, String version);

  record ResolvedProjectArtifact(
      SourceModuleIndex.Module module, LocalArtifactInspection.InspectedArtifact artifact) {}
}
