package org.zalava.modules.development.application.port.out;

import org.zalava.modules.development.DevelopmentWorkspace;
import org.zalava.modules.development.ModuleDevelopmentRequest;

/** Boundary for materializing SEA-owned request files in a user-selected workspace. */
public interface DevelopmentWorkspacePort {

  DevelopmentWorkspace materialize(ModuleDevelopmentRequest request, String workspaceRoot);
}
