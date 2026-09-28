package org.zalava.development.application.port.out;

import org.zalava.development.DevelopmentWorkspace;
import org.zalava.development.ModuleDevelopmentRequest;

/** Boundary for materializing SEA-owned request files in a user-selected workspace. */
public interface DevelopmentWorkspacePort {

  DevelopmentWorkspace materialize(ModuleDevelopmentRequest request, String workspaceRoot);
}
