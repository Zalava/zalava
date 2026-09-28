package org.zalava.ui;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.development.application.port.in.DevelopmentWorkspaceExport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.ObjectMapper;

/** Local-control adapter for the manual module-development workflow. */
@Controller
@RequestMapping(SeaControlUiController.PATH + "/development-requests")
class DevelopmentRequestControlController {

  private static final int MAX_CONTRACT_JSON_LENGTH = 50_000;
  private static final int MAX_FIELD_LENGTH = 2_000;
  private static final int MAX_ERROR_LENGTH = 300;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final SeaControlUiController workspaceView;
  private final DevelopmentRequestManagement requests;
  private final DevelopmentWorkspaceExport workspaces;
  private final DevelopmentCandidateSubmission candidates;

  DevelopmentRequestControlController(
      SeaControlUiController workspaceView,
      DevelopmentRequestManagement requests,
      DevelopmentWorkspaceExport workspaces,
      DevelopmentCandidateSubmission candidates) {
    this.workspaceView = workspaceView;
    this.requests = requests;
    this.workspaces = workspaces;
    this.candidates = candidates;
  }

  @PostMapping
  String create(@RequestParam String contractJson, @RequestParam String reason, Model model) {
    return render(
        model,
        () ->
            SeaControlUiController.DevelopmentRequestEntry.from(
                requests.create(
                    contract(contractJson), required(reason, "Reason", MAX_FIELD_LENGTH))));
  }

  @PostMapping("/revise")
  String revise(
      @RequestParam String requestId,
      @RequestParam String contractJson,
      @RequestParam String reason,
      Model model) {
    return render(
        model,
        () ->
            SeaControlUiController.DevelopmentRequestEntry.from(
                requests.revise(
                    id(requestId),
                    contract(contractJson),
                    required(reason, "Reason", MAX_FIELD_LENGTH))));
  }

  @PostMapping("/{requestId}/exports")
  String export(@PathVariable String requestId, @RequestParam String workspaceRoot, Model model) {
    return render(model, () -> exported(requestId, workspaceRoot));
  }

  @PostMapping("/export")
  String exportForm(
      @RequestParam String requestId, @RequestParam String workspaceRoot, Model model) {
    return export(requestId, workspaceRoot, model);
  }

  @PostMapping("/{requestId}/begin")
  String begin(@PathVariable String requestId, Model model) {
    return render(
        model,
        () ->
            SeaControlUiController.DevelopmentRequestEntry.from(
                requests.transition(
                    id(requestId),
                    org.zalava.development.DevelopmentRequestStatus.IN_DEVELOPMENT)));
  }

  @PostMapping("/begin")
  String beginForm(@RequestParam String requestId, Model model) {
    return begin(requestId, model);
  }

  @PostMapping("/{requestId}/candidates")
  String submitCandidate(
      @PathVariable String requestId, @RequestParam String artifactPath, Model model) {
    return render(
        model,
        () ->
            SeaControlUiController.DevelopmentRequestEntry.from(
                candidates.submit(
                    id(requestId), required(artifactPath, "Candidate path", MAX_FIELD_LENGTH))));
  }

  @PostMapping("/candidate")
  String submitCandidateForm(
      @RequestParam String requestId, @RequestParam String artifactPath, Model model) {
    return submitCandidate(requestId, artifactPath, model);
  }

  @PostMapping("/{requestId}/inspect")
  String inspect(@PathVariable String requestId, Model model) {
    return render(
        model,
        () -> SeaControlUiController.DevelopmentRequestEntry.from(requests.get(id(requestId))));
  }

  @PostMapping("/inspect")
  String inspectForm(@RequestParam String requestId, Model model) {
    return inspect(requestId, model);
  }

  private SeaControlUiController.DevelopmentRequestEntry exported(
      String requestId, String workspaceRoot) {
    var workspace =
        workspaces.export(
            id(requestId), required(workspaceRoot, "Workspace path", MAX_FIELD_LENGTH));
    return SeaControlUiController.DevelopmentRequestEntry.exported(
        workspace.requestId().value(), workspace.sha256().keySet().stream().sorted().toList());
  }

  private String render(Model model, DevelopmentOperation operation) {
    try {
      return workspaceView.renderWorkspace(model, null, operation.run());
    } catch (Exception ex) {
      return workspaceView.renderWorkspace(model, error(ex), null);
    }
  }

  private static ModuleDevelopmentContract contract(String value) throws Exception {
    return JSON.readValue(
        required(value, "Contract JSON", MAX_CONTRACT_JSON_LENGTH),
        ModuleDevelopmentContract.class);
  }

  private static DevelopmentRequestId id(String value) {
    return new DevelopmentRequestId(required(value, "Request id", 128));
  }

  private static String required(String value, String name, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength)
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + maximumLength + " characters");
    return value;
  }

  private static String error(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Development workflow operation failed";
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() <= MAX_ERROR_LENGTH
        ? normalized
        : normalized.substring(0, MAX_ERROR_LENGTH) + "...";
  }

  @FunctionalInterface
  private interface DevelopmentOperation {
    SeaControlUiController.DevelopmentRequestEntry run() throws Exception;
  }
}
