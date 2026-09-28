package org.zalava.development.adapter.in.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.DevelopmentRequestStatus;
import org.zalava.development.DevelopmentWorkspace;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.ModuleDevelopmentRequest;
import org.zalava.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.development.application.port.in.DevelopmentWorkspaceExport;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.ObjectMapper;

/** Bounded agent adapter for the manual external-module development workflow. */
public final class DevelopmentRequestTools {

  private static final int MAX_REASON_LENGTH = 2_000;
  private static final int MAX_PATH_LENGTH = 2_000;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final DevelopmentRequestManagement requests;
  private final DevelopmentWorkspaceExport workspaces;
  private final DevelopmentCandidateSubmission candidates;

  public DevelopmentRequestTools(
      DevelopmentRequestManagement requests,
      DevelopmentWorkspaceExport workspaces,
      DevelopmentCandidateSubmission candidates) {
    this.requests = requests;
    this.workspaces = workspaces;
    this.candidates = candidates;
  }

  @Tool(
      name = "createModuleDevelopmentRequest",
      description =
          "Creates SEA's authoritative external-module development request from a structured complete contract. Gather unresolved product decisions before calling it. If the user supplied an external workspace, use the returned requestId to call exportModuleDevelopmentWorkspace next. SEA does not start Codex or create a repository.")
  public String create(
      @ToolParam(
              description =
                  "Complete structured contract: module identity and concrete semantic version such as 1.0.0, purpose, target API, tools with JSON schemas/examples, expected errors, acceptance scenarios, operational requirements, and delivery requirements")
          ModuleDevelopmentContract contract,
      @ToolParam(description = "Why this module is requested") String reason) {
    return json(summary(requests.create(contract, required(reason, "reason", MAX_REASON_LENGTH))));
  }

  @Tool(
      name = "reviseModuleDevelopmentRequest",
      description =
          "Revises the authoritative structured contract after candidate feedback. It never changes user files in an exported workspace.")
  public String revise(
      @ToolParam(description = "SEA development request id") String requestId,
      @ToolParam(description = "Complete replacement structured module development contract")
          ModuleDevelopmentContract contract,
      @ToolParam(description = "Reason for this revision") String reason) {
    return json(
        summary(
            requests.revise(
                id(requestId), contract, required(reason, "reason", MAX_REASON_LENGTH))));
  }

  @Tool(
      name = "exportModuleDevelopmentWorkspace",
      description =
          "Exports SEA-owned request material to an explicit user-selected directory. It never overwrites implementation files outside .sea-request. After success, call beginModuleDevelopment before relaying the returned workspace path, Codex command, and initial prompt to the user; SEA never launches or monitors Codex.")
  public String export(
      @ToolParam(description = "SEA development request id") String requestId,
      @ToolParam(description = "Explicit external workspace directory") String workspaceRoot) {
    DevelopmentWorkspace workspace =
        workspaces.export(id(requestId), required(workspaceRoot, "workspaceRoot", MAX_PATH_LENGTH));
    return json(
        Map.of(
            "requestId", workspace.requestId().value(),
            "exported", true,
            "workspacePath", workspace.root(),
            "files", workspace.sha256().keySet().stream().sorted().toList(),
            "codexCommand", "cd " + workspace.root() + " && codex",
            "initialPrompt", "Read .sea-request/CODEX_TASK.md and begin the implementation.",
            "nextSteps",
                java.util.List.of(
                    "Open a terminal in the exported workspace.",
                    "Start Codex with the returned command.",
                    "Give Codex the returned initial prompt and answer any unresolved product questions.")));
  }

  @Tool(
      name = "beginModuleDevelopment",
      description =
          "Explicitly records that the user is beginning manual development after export. It transitions only an EXPORTED request to IN_DEVELOPMENT, which permits later candidate submission. It never launches Codex, submits a candidate, installs, or activates a module.")
  public String begin(@ToolParam(description = "SEA development request id") String requestId) {
    return json(
        summary(requests.transition(id(requestId), DevelopmentRequestStatus.IN_DEVELOPMENT)));
  }

  @Tool(
      name = "submitModuleDevelopmentCandidate",
      description =
          "Submits one trusted local module JAR for bounded evaluation. A result still requires a separate installation approval; this tool never installs or activates a module.")
  public String submit(
      @ToolParam(description = "SEA development request id") String requestId,
      @ToolParam(description = "Trusted local candidate JAR path") String artifactPath) {
    return json(
        summary(
            candidates.submit(
                id(requestId), required(artifactPath, "artifactPath", MAX_PATH_LENGTH))));
  }

  @Tool(
      name = "inspectModuleDevelopmentRequest",
      description =
          "Returns bounded request revision and candidate evaluation evidence, including the explicit installation-approval handoff.")
  public String inspect(@ToolParam(description = "SEA development request id") String requestId) {
    return json(summary(requests.get(id(requestId))));
  }

  private static DevelopmentRequestId id(String value) {
    return new DevelopmentRequestId(required(value, "requestId", 128));
  }

  private static String required(String value, String name, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength)
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + maxLength + " characters");
    return value;
  }

  private static Map<String, Object> summary(ModuleDevelopmentRequest request) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("requestId", request.id().value());
    result.put("status", request.status().name());
    result.put("revision", request.currentRevision().number());
    result.put("moduleId", request.currentRevision().contract().module().moduleId());
    result.put(
        "candidateAttempts",
        request.candidateAttempts().stream()
            .map(
                attempt ->
                    Map.of(
                        "number",
                        attempt.number(),
                        "sha256",
                        attempt.sha256Digest(),
                        "accepted",
                        attempt.evaluation() != null && attempt.evaluation().accepted(),
                        "evidence",
                        attempt.evaluation() == null
                            ? java.util.List.of()
                            : attempt.evaluation().evidence(),
                        "invocations",
                        attempt.evaluation() == null
                            ? java.util.List.of()
                            : attempt.evaluation().invocations(),
                        "jsonReport",
                        attempt.evaluation() == null ? "" : attempt.evaluation().jsonReport(),
                        "markdownReport",
                        attempt.evaluation() == null ? "" : attempt.evaluation().markdownReport()))
            .toList());
    result.put(
        "installationApproval",
        request.status() == DevelopmentRequestStatus.READY_TO_INSTALL
            ? "required_before_installation"
            : "not_available");
    return result;
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to serialize development request result", ex);
    }
  }
}
