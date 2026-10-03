package org.zalava.modules.development.adapter.in.http;

import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.DevelopmentRequestStatus;
import org.zalava.modules.development.ModuleDevelopmentContract;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.modules.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.modules.development.application.port.in.DevelopmentWorkspaceExport;

/** Development/test-only HTTP adapter for the authoritative module-development workflow. */
@RestController
@RequestMapping("/api/zalava/development-requests")
@Profile({"dev", "test"})
public class DevelopmentRequestAdminController {

  private static final int MAX_FIELD_LENGTH = 2_000;

  private final DevelopmentRequestManagement developmentRequests;
  private final DevelopmentWorkspaceExport developmentWorkspaces;
  private final DevelopmentCandidateSubmission developmentCandidates;

  public DevelopmentRequestAdminController(
      DevelopmentRequestManagement developmentRequests,
      DevelopmentWorkspaceExport developmentWorkspaces,
      DevelopmentCandidateSubmission developmentCandidates) {
    this.developmentRequests = developmentRequests;
    this.developmentWorkspaces = developmentWorkspaces;
    this.developmentCandidates = developmentCandidates;
  }

  @PostMapping
  public ResponseEntity<DevelopmentRequestResponse> create(
      @RequestBody DevelopmentRequestCommand request) {
    try {
      ModuleDevelopmentRequest created =
          developmentRequests.create(request.contract(), required(request.reason(), "reason"));
      return ResponseEntity.created(
              URI.create("/api/zalava/development-requests/" + created.id().value()))
          .body(DevelopmentRequestResponse.from(created));
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex);
    }
  }

  @GetMapping("/{requestId}")
  public DevelopmentRequestResponse get(@PathVariable String requestId) {
    return DevelopmentRequestResponse.from(request(requestId));
  }

  @PostMapping("/{requestId}/revisions")
  public DevelopmentRequestResponse revise(
      @PathVariable String requestId, @RequestBody DevelopmentRequestCommand request) {
    try {
      return DevelopmentRequestResponse.from(
          developmentRequests.revise(
              id(requestId), request.contract(), required(request.reason(), "reason")));
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex);
    }
  }

  @PostMapping("/{requestId}/exports")
  public DevelopmentWorkspaceResponse export(
      @PathVariable String requestId, @RequestBody DevelopmentWorkspaceCommand request) {
    try {
      var workspace =
          developmentWorkspaces.export(
              id(requestId), required(request.workspaceRoot(), "workspaceRoot"));
      return new DevelopmentWorkspaceResponse(
          workspace.requestId().value(), workspace.sha256().keySet().stream().sorted().toList());
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex);
    }
  }

  @PostMapping("/{requestId}/candidates")
  public DevelopmentRequestResponse submitCandidate(
      @PathVariable String requestId, @RequestBody DevelopmentCandidateCommand request) {
    try {
      return DevelopmentRequestResponse.from(
          developmentCandidates.submit(
              id(requestId), required(request.artifactPath(), "artifactPath")));
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex);
    }
  }

  @PostMapping("/{requestId}/installation-approval")
  public InstallationApprovalResponse requestInstallationApproval(@PathVariable String requestId) {
    ModuleDevelopmentRequest request = request(requestId);
    if (request.status() != DevelopmentRequestStatus.READY_TO_INSTALL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Installation approval is available only after accepted candidate evaluation");
    }
    return new InstallationApprovalResponse(
        request.id().value(),
        "required_before_installation",
        "Candidate acceptance does not install or activate a module. Use the applicable installation approval workflow.");
  }

  private ModuleDevelopmentRequest request(String requestId) {
    try {
      return developmentRequests.get(id(requestId));
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  private static DevelopmentRequestId id(String value) {
    return new DevelopmentRequestId(value);
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank() || value.length() > MAX_FIELD_LENGTH) {
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + MAX_FIELD_LENGTH + " characters");
    }
    return value;
  }

  private static ResponseStatusException badRequest(IllegalArgumentException ex) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
  }

  public record DevelopmentRequestCommand(ModuleDevelopmentContract contract, String reason) {}

  public record DevelopmentWorkspaceCommand(String workspaceRoot) {}

  public record DevelopmentCandidateCommand(String artifactPath) {}

  public record DevelopmentWorkspaceResponse(String requestId, List<String> files) {}

  public record InstallationApprovalResponse(String requestId, String status, String message) {}

  public record DevelopmentRequestResponse(
      String requestId,
      String moduleId,
      String status,
      int revision,
      List<CandidateAttemptResponse> candidateAttempts,
      String installationApproval) {
    static DevelopmentRequestResponse from(ModuleDevelopmentRequest request) {
      return new DevelopmentRequestResponse(
          request.id().value(),
          request.currentRevision().contract().module().moduleId(),
          request.status().name().toLowerCase(),
          request.currentRevision().number(),
          request.candidateAttempts().stream().map(CandidateAttemptResponse::from).toList(),
          request.status() == DevelopmentRequestStatus.READY_TO_INSTALL
              ? "required_before_installation"
              : "not_available");
    }
  }

  public record CandidateAttemptResponse(
      int number,
      String sha256Digest,
      Boolean accepted,
      List<String> evidence,
      String jsonReport,
      String markdownReport) {
    static CandidateAttemptResponse from(ModuleDevelopmentRequest.CandidateAttempt attempt) {
      return new CandidateAttemptResponse(
          attempt.number(),
          attempt.sha256Digest(),
          attempt.evaluation() == null ? null : attempt.evaluation().accepted(),
          attempt.evaluation() == null ? List.of() : attempt.evaluation().evidence(),
          attempt.evaluation() == null ? null : attempt.evaluation().jsonReport(),
          attempt.evaluation() == null ? null : attempt.evaluation().markdownReport());
    }
  }
}
