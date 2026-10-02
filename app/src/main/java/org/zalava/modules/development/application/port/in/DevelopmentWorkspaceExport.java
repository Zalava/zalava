package org.zalava.modules.development.application.port.in;

import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.DevelopmentWorkspace;

/** Exports informational request material without changing the authoritative request. */
public interface DevelopmentWorkspaceExport {

  DevelopmentWorkspace export(DevelopmentRequestId requestId, String workspaceRoot);
}
