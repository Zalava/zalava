package org.zalava.development.application;

import org.zalava.control.application.AdministratorControlAuthorization;
import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.DevelopmentRequestStatus;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.ModuleDevelopmentRequest;
import org.zalava.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.development.application.port.in.DevelopmentWorkspaceExport;

/** Applies administrator authorization to the instance-wide development-request workflow. */
public final class AdministratorAuthorizedDevelopmentRequests {
  private AdministratorAuthorizedDevelopmentRequests() {}

  public static DevelopmentRequestManagement management(
      DevelopmentRequestManagement delegate, AdministratorControlAuthorization authorization) {
    return new DevelopmentRequestManagement() {
      @Override
      public ModuleDevelopmentRequest create(ModuleDevelopmentContract contract, String reason) {
        return authorization.call(
            "development-request:" + contract.module().moduleId(),
            () -> delegate.create(contract, reason));
      }

      @Override
      public ModuleDevelopmentRequest get(DevelopmentRequestId id) {
        return authorization.call("development-request:" + id.value(), () -> delegate.get(id));
      }

      @Override
      public ModuleDevelopmentRequest revise(
          DevelopmentRequestId id, ModuleDevelopmentContract contract, String reason) {
        return authorization.call(
            "development-request-revise:" + id.value(),
            () -> delegate.revise(id, contract, reason));
      }

      @Override
      public ModuleDevelopmentRequest transition(
          DevelopmentRequestId id, DevelopmentRequestStatus target) {
        return authorization.call(
            "development-request-transition:" + id.value() + ":" + target,
            () -> delegate.transition(id, target));
      }

      @Override
      public ModuleDevelopmentRequest cancel(DevelopmentRequestId id) {
        return authorization.call(
            "development-request-cancel:" + id.value(), () -> delegate.cancel(id));
      }
    };
  }

  public static DevelopmentCandidateSubmission candidates(
      DevelopmentCandidateSubmission delegate, AdministratorControlAuthorization authorization) {
    return (requestId, artifactPath) ->
        authorization.call(
            "development-request-candidate:" + requestId.value(),
            () -> delegate.submit(requestId, artifactPath));
  }

  public static DevelopmentWorkspaceExport workspaces(
      DevelopmentWorkspaceExport delegate, AdministratorControlAuthorization authorization) {
    return (requestId, workspaceRoot) ->
        authorization.call(
            "development-request-export:" + requestId.value(),
            () -> delegate.export(requestId, workspaceRoot));
  }
}
