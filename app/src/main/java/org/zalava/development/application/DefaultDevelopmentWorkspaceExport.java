package org.zalava.development.application;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.DevelopmentRequestStatus;
import org.zalava.development.DevelopmentWorkspace;
import org.zalava.development.ModuleDevelopmentRequest;
import org.zalava.development.application.port.in.DevelopmentWorkspaceExport;
import org.zalava.development.application.port.out.DevelopmentRequestStore;
import org.zalava.development.application.port.out.DevelopmentWorkspacePort;

public final class DefaultDevelopmentWorkspaceExport implements DevelopmentWorkspaceExport {

  private final DevelopmentRequestStore requests;
  private final DevelopmentWorkspacePort workspaces;

  public DefaultDevelopmentWorkspaceExport(
      DevelopmentRequestStore requests, DevelopmentWorkspacePort workspaces) {
    this.requests = requests;
    this.workspaces = workspaces;
  }

  @Override
  public synchronized DevelopmentWorkspace export(
      DevelopmentRequestId requestId, String workspaceRoot) {
    ModuleDevelopmentRequest request = requests.get(requestId);
    DevelopmentWorkspace workspace = workspaces.materialize(request, workspaceRoot);
    if (request.status() == DevelopmentRequestStatus.PREPARED) {
      requests.save(request.transitionTo(DevelopmentRequestStatus.EXPORTED));
    }
    return workspace;
  }
}
