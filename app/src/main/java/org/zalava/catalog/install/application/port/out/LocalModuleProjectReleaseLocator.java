package org.zalava.catalog.install.application.port.out;

import org.zalava.catalog.ModuleReleaseSelection;

/** Finds the immutable release metadata and binary inside a trusted built project directory. */
public interface LocalModuleProjectReleaseLocator {
  ResolvedRelease resolve(String projectDirectory, String moduleId, String version);

  record ResolvedRelease(
      ModuleReleaseSelection.SelectedRelease release,
      LocalArtifactInspection.InspectedArtifact artifact) {}
}
