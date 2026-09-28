package org.zalava.development.application.port.in;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.DevelopmentWorkspace;

/** Exports informational request material without changing the authoritative request. */
public interface DevelopmentWorkspaceExport {

  DevelopmentWorkspace export(DevelopmentRequestId requestId, String workspaceRoot);
}
