package org.zalava.modules.development.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.zalava.*;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.ModuleDevelopmentContract;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.runtime.ExternalSeaModuleLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** In-process pilot host: contract evidence only, never a safety certification or installer. */
public final class DevelopmentCandidateEvaluator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_TRANSCRIPT_JSON_LENGTH = 16_000;
  private final Clock clock;

  public DevelopmentCandidateEvaluator(Clock clock) {
    this.clock = clock;
  }

  public CandidateEvaluation evaluate(
      ModuleDevelopmentRequest request, LocalArtifactInspection.InspectedArtifact artifact) {
    List<String> evidence = new ArrayList<>();
    List<CandidateEvaluation.Invocation> invocations = new ArrayList<>();
    List<CandidateEvaluation.Requirement> requirements = new ArrayList<>();
    try (ExternalSeaModuleLoader loader =
        new ExternalSeaModuleLoader(() -> List.of(enabled(request, artifact)))) {
      List<ZalavaModule> modules = loader.loadModules();
      ModuleDevelopmentContract contract = request.currentRevision().contract();
      ZalavaModule module = modules.getFirst();
      if (!contract.module().moduleId().equals(module.descriptor().moduleId()))
        throw new IllegalStateException("Requested module identity was not exposed");
      List<ZalavaProvider> providers =
          module.providerFactories().stream()
              .flatMap(factory -> factory.createProviders(ProviderFactoryContext.empty()).stream())
              .toList();
      ZalavaProvider provider =
          providers.stream()
              .filter(candidate -> candidate.capabilities().supportsTools())
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No tool-capable provider was exposed"));
      requireRequestedToolContracts(contract, provider);
      ModuleDevelopmentContract.Tool requestedTool = contract.tools().getFirst();
      ZalavaToolDescriptor tool =
          provider.listTools().stream()
              .filter(candidate -> candidate.name().equals(requestedTool.name()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Requested tool was not exposed: " + requestedTool.name()));
      if (tool.inputSchema().isEmpty())
        throw new IllegalStateException("Requested tool exposed no input schema");
      JsonNode input = JSON.readTree(requestedTool.examples().getFirst().inputJson());
      ContractSchemaValidation.requireValid(
          requestedTool.inputSchema(), input, "Requested tool example");
      InvocationExecution execution = callWithinLimit(provider, tool.name(), input, contract);
      ZalavaOperationResult response = execution.response();
      invocations.add(
          new CandidateEvaluation.Invocation(
              tool.name(),
              boundedSanitizedJson(input),
              execution.responseJson(),
              response == null ? null : response.success(),
              execution.elapsedMillis()));
      if (execution.failureMessage() != null)
        throw new IllegalStateException(execution.failureMessage());
      if (!response.success())
        throw new IllegalStateException("Requested tool did not return success");
      ContractSchemaValidation.requireValid(
          requestedTool.outputSchema(),
          JSON.valueToTree(response.content()),
          "Successful response");
      requirements.add(
          verified(
              "successful-output-schema", requestedTool.outputSchema(), execution.responseJson()));
      verifyAcceptanceScenarios(provider, tool, requestedTool, contract, invocations, requirements);
      verifyGeneratedInvalidInputs(
          provider, tool, requestedTool, contract, input, invocations, requirements);
      verifyExpectedUnknownToolError(provider, contract, requirements);
      verifyRepeatedInvocation(provider, tool, input, contract, invocations, requirements);
      addDeclaredOperationalRequirements(contract, requirements);
      requirements.add(
          verified(
              "clean-unload", "module loader closes without error", "loader remains closeable"));
      evidence.add("Loaded module " + module.descriptor().moduleId());
      evidence.add(
          "Strictly matched requested and exposed input schemas for "
              + contract.tools().size()
              + " tool(s)");
      evidence.add(
          "Observed successful requested-tool response satisfying its output schema and satisfying the acceptance scenario");
      return report(request, artifact, evidence, invocations, requirements);
    } catch (Exception ex) {
      evidence.add(
          "REJECTED: "
              + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
      requirements.add(
          failed("validation", "all required validation checks pass", ex.getMessage()));
      return report(request, artifact, evidence, invocations, requirements);
    }
  }

  private static void requireRequestedToolContracts(
      ModuleDevelopmentContract contract, ZalavaProvider provider) {
    Map<String, ZalavaToolDescriptor> exposed =
        provider.listTools().stream()
            .collect(java.util.stream.Collectors.toMap(ZalavaToolDescriptor::name, tool -> tool));
    if (!exposed
        .keySet()
        .equals(
            contract.tools().stream()
                .map(ModuleDevelopmentContract.Tool::name)
                .collect(java.util.stream.Collectors.toSet()))) {
      throw new IllegalStateException("Requested and exposed tool names differ");
    }
    for (ModuleDevelopmentContract.Tool requested : contract.tools()) {
      ContractSchemaValidation.requireEquivalentInputSchema(
          requested.inputSchema(), exposed.get(requested.name()).inputSchema(), requested.name());
    }
  }

  private void verifyGeneratedInvalidInputs(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      ModuleDevelopmentContract.Tool requestedTool,
      ModuleDevelopmentContract contract,
      JsonNode input,
      List<CandidateEvaluation.Invocation> invocations,
      List<CandidateEvaluation.Requirement> requirements) {
    for (JsonNode invalidInput :
        ContractSchemaValidation.invalidInputs(requestedTool.inputSchema(), input)) {
      InvocationExecution invalid = callWithinLimit(provider, tool.name(), invalidInput, contract);
      ZalavaOperationResult response = invalid.response();
      invocations.add(
          new CandidateEvaluation.Invocation(
              tool.name(),
              boundedSanitizedJson(invalidInput),
              invalid.responseJson(),
              response == null ? null : response.success(),
              invalid.elapsedMillis()));
      if (invalid.failureMessage() != null || response.success()) {
        throw new IllegalStateException(
            "Generated invalid input was not rejected by tool " + tool.name());
      }
      requirements.add(
          verified(
              "generated-invalid-input",
              "tool rejects schema-derived invalid input",
              invalid.responseJson()));
    }
  }

  private InvocationExecution callWithinLimit(
      ZalavaProvider provider, String tool, JsonNode input, ModuleDevelopmentContract contract) {
    long timeout =
        contract.operationalRequirements().timeoutMs() == null
            ? 1_000L
            : contract.operationalRequirements().timeoutMs();
    long startedAt = System.nanoTime();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<ZalavaOperationResult> invocation =
          executor.submit(() -> provider.callTool(tool, input, InvocationContext.system()));
      ZalavaOperationResult result = invocation.get(timeout, TimeUnit.MILLISECONDS);
      String responseJson = boundedSanitizedJson(JSON.valueToTree(result.content()));
      Long maximumResponseBytes = contract.operationalRequirements().maximumResponseBytes();
      if (maximumResponseBytes != null
          && JSON.valueToTree(result.content()).toString().getBytes(StandardCharsets.UTF_8).length
              > maximumResponseBytes) {
        return new InvocationExecution(
            result,
            elapsedMillis(startedAt),
            responseJson,
            "Tool response exceeded maximum response bytes");
      }
      return new InvocationExecution(result, elapsedMillis(startedAt), responseJson, null);
    } catch (TimeoutException ex) {
      return new InvocationExecution(
          null,
          elapsedMillis(startedAt),
          "{\"error\":\"Tool invocation exceeded timeout\"}",
          "Tool invocation exceeded timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return new InvocationExecution(
          null,
          elapsedMillis(startedAt),
          "{\"error\":\"Tool invocation interrupted\"}",
          "Tool invocation interrupted");
    } catch (ExecutionException ex) {
      return new InvocationExecution(
          null,
          elapsedMillis(startedAt),
          "{\"error\":\"Tool invocation failed\"}",
          "Tool invocation failed");
    }
  }

  private void verifyAcceptanceScenarios(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      ModuleDevelopmentContract.Tool requestedTool,
      ModuleDevelopmentContract contract,
      List<CandidateEvaluation.Invocation> invocations,
      List<CandidateEvaluation.Requirement> requirements)
      throws Exception {
    for (ModuleDevelopmentContract.AcceptanceScenario scenario : contract.acceptanceScenarios()) {
      JsonNode input = JSON.readTree(scenario.requestJson());
      InvocationExecution execution = callWithinLimit(provider, tool.name(), input, contract);
      ZalavaOperationResult response = execution.response();
      invocations.add(
          new CandidateEvaluation.Invocation(
              tool.name(),
              boundedSanitizedJson(input),
              execution.responseJson(),
              response == null ? null : response.success(),
              execution.elapsedMillis()));
      if (execution.failureMessage() != null)
        throw new IllegalStateException(
            "Scenario " + scenario.id() + ": " + execution.failureMessage());
      if (scenario.expectedErrorCode() != null && !scenario.expectedErrorCode().isBlank()) {
        requireExpectedError(response, scenario.expectedErrorCode(), "scenario " + scenario.id());
        requirements.add(
            verified(
                "scenario." + scenario.id(),
                "error code " + scenario.expectedErrorCode(),
                execution.responseJson()));
        continue;
      }
      if (!response.success())
        throw new IllegalStateException(
            "Scenario " + scenario.id() + " returned an error: " + execution.responseJson());
      JsonNode content = JSON.valueToTree(response.content());
      ContractSchemaValidation.requireValid(
          requestedTool.outputSchema(), content, "Scenario " + scenario.id() + " response");
      for (ModuleDevelopmentContract.ResponseAssertion assertion : scenario.assertions()) {
        AcceptanceAssertions.requireSatisfied(assertion, content);
      }
      requirements.add(
          verified(
              "scenario." + scenario.id(),
              "all assertions and output schema",
              execution.responseJson()));
    }
  }

  private void verifyExpectedUnknownToolError(
      ZalavaProvider provider,
      ModuleDevelopmentContract contract,
      List<CandidateEvaluation.Requirement> requirements) {
    if (contract.expectedErrors().isEmpty()) return;
    ZalavaOperationResult response =
        provider.callTool(
            "__sea_expected_error__", JSON.createObjectNode(), InvocationContext.system());
    String expected = contract.expectedErrors().getFirst().code();
    requireExpectedError(response, expected, "expected error");
    requirements.add(
        verified(
            "expected-error." + expected,
            "error code " + expected,
            boundedSanitizedJson(JSON.valueToTree(response.content()))));
  }

  private void verifyRepeatedInvocation(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      JsonNode input,
      ModuleDevelopmentContract contract,
      List<CandidateEvaluation.Invocation> invocations,
      List<CandidateEvaluation.Requirement> requirements) {
    InvocationExecution repeated = callWithinLimit(provider, tool.name(), input, contract);
    ZalavaOperationResult response = repeated.response();
    invocations.add(
        new CandidateEvaluation.Invocation(
            tool.name(),
            boundedSanitizedJson(input),
            repeated.responseJson(),
            response == null ? null : response.success(),
            repeated.elapsedMillis()));
    if (repeated.failureMessage() != null || response == null || !response.success()) {
      throw new IllegalStateException("Repeated invocation did not complete successfully");
    }
    requirements.add(
        verified(
            "repeated-invocation",
            "second invocation succeeds without host termination",
            repeated.responseJson()));
  }

  private static void requireExpectedError(
      ZalavaOperationResult response, String expectedCode, String requirement) {
    if (response == null || response.success())
      throw new IllegalStateException(
          "Expected error "
              + expectedCode
              + " was incorrectly returned as success for "
              + requirement);
    JsonNode envelope = JSON.valueToTree(response.content());
    if (!envelope.isObject()
        || !envelope.path("code").isString()
        || envelope.path("code").stringValue("").isBlank()) {
      throw new IllegalStateException("Expected error envelope is invalid for " + requirement);
    }
    if (!expectedCode.equals(envelope.path("code").stringValue(""))) {
      throw new IllegalStateException(
          "Expected error code "
              + expectedCode
              + " but observed "
              + envelope.path("code").stringValue(""));
    }
  }

  private static void addDeclaredOperationalRequirements(
      ModuleDevelopmentContract contract, List<CandidateEvaluation.Requirement> requirements) {
    ModuleDevelopmentContract.OperationalRequirements operational =
        contract.operationalRequirements();
    if (Boolean.TRUE.equals(operational.networkAccessRequired()))
      requirements.add(
          declared(
              "operational.network-access",
              "network access",
              "declared; external failure modes are not injectable"));
    if (!operational.namedSecretsRequired().isEmpty())
      requirements.add(
          declared(
              "operational.named-secrets",
              String.join(",", operational.namedSecretsRequired()),
              "declared; credentials are not exercised"));
    if (Boolean.TRUE.equals(operational.filesystemAccessRequired()))
      requirements.add(
          declared(
              "operational.filesystem-access",
              "filesystem access",
              "declared; external scope is not exercised"));
    if (Boolean.TRUE.equals(operational.processExecutionRequired()))
      requirements.add(
          declared(
              "operational.process-execution",
              "process execution",
              "declared; external process behaviour is not exercised"));
  }

  private CandidateEvaluation report(
      ModuleDevelopmentRequest request,
      LocalArtifactInspection.InspectedArtifact artifact,
      List<String> evidence,
      List<CandidateEvaluation.Invocation> invocations,
      List<CandidateEvaluation.Requirement> requirements) {
    CandidateEvaluation.Decision decision =
        requirements.stream()
                .anyMatch(
                    requirement ->
                        requirement.status() == CandidateEvaluation.RequirementStatus.FAILED)
            ? CandidateEvaluation.Decision.REJECTED
            : requirements.stream()
                    .anyMatch(
                        requirement ->
                            requirement.status() != CandidateEvaluation.RequirementStatus.VERIFIED)
                ? CandidateEvaluation.Decision.ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS
                : CandidateEvaluation.Decision.ACCEPTED;
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("requestId", request.id().value());
    report.put("checksum", artifact.sha256Digest());
    report.put("decision", decision);
    report.put("evidence", evidence);
    report.put("invocations", invocations);
    report.put("requirements", requirements);
    report.put("validatorVersion", "val-02");
    String json = JSON.valueToTree(report).toString();
    String markdown =
        "# Module candidate evaluation\n\nDecision: **"
            + decision
            + "**\n\n- Request: `"
            + request.id().value()
            + "`\n- Checksum: `"
            + artifact.sha256Digest()
            + "`\n\n"
            + String.join("\n", evidence.stream().map(value -> "- " + value).toList())
            + requirementMarkdown(requirements)
            + invocationMarkdown(invocations);
    return new CandidateEvaluation(
        decision, clock.instant(), evidence, json, markdown, invocations, requirements);
  }

  private static CandidateEvaluation.Requirement verified(
      String id, String expected, String actual) {
    return new CandidateEvaluation.Requirement(
        id, CandidateEvaluation.RequirementStatus.VERIFIED, expected, actual);
  }

  private static CandidateEvaluation.Requirement declared(
      String id, String expected, String actual) {
    return new CandidateEvaluation.Requirement(
        id, CandidateEvaluation.RequirementStatus.DECLARED, expected, actual);
  }

  private static CandidateEvaluation.Requirement failed(String id, String expected, String actual) {
    return new CandidateEvaluation.Requirement(
        id, CandidateEvaluation.RequirementStatus.FAILED, expected, actual);
  }

  private static String requirementMarkdown(List<CandidateEvaluation.Requirement> requirements) {
    if (requirements.isEmpty()) return "";
    return "\n\n## Requirement evidence\n\n"
        + String.join(
            "\n",
            requirements.stream()
                .map(
                    requirement ->
                        "- `"
                            + requirement.id()
                            + "`: **"
                            + requirement.status()
                            + "**; expected `"
                            + requirement.expected()
                            + "`; observed `"
                            + requirement.actual()
                            + "`")
                .toList());
  }

  private static String invocationMarkdown(List<CandidateEvaluation.Invocation> invocations) {
    if (invocations.isEmpty()) return "";
    CandidateEvaluation.Invocation invocation = invocations.getLast();
    return "\n\n## Requested tool invocation\n\n- Tool: `"
        + invocation.toolName()
        + "`\n- Input: `"
        + invocation.inputJson()
        + "`\n- Success: `"
        + invocation.success()
        + "`\n- Duration: `"
        + invocation.elapsedMillis()
        + " ms`\n- Response: `"
        + invocation.responseJson()
        + "`";
  }

  private static long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private static String boundedSanitizedJson(JsonNode value) {
    String rendered = sanitize(value).toString();
    return rendered.length() <= MAX_TRANSCRIPT_JSON_LENGTH
        ? rendered
        : rendered.substring(0, MAX_TRANSCRIPT_JSON_LENGTH) + "… [truncated]";
  }

  private static JsonNode sanitize(JsonNode value) {
    if (value == null || value.isNull() || value.isValueNode())
      return value == null ? JSON.nullNode() : value.deepCopy();
    if (value.isArray()) {
      ArrayNode sanitized = JSON.createArrayNode();
      value.forEach(element -> sanitized.add(sanitize(element)));
      return sanitized;
    }
    ObjectNode sanitized = JSON.createObjectNode();
    value
        .properties()
        .forEach(
            entry ->
                sanitized.set(
                    entry.getKey(),
                    isSensitiveKey(entry.getKey())
                        ? JSON.getNodeFactory().stringNode("[REDACTED]")
                        : sanitize(entry.getValue())));
    return sanitized;
  }

  private static boolean isSensitiveKey(String key) {
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("password")
        || normalized.contains("credential")
        || normalized.contains("authorization")
        || normalized.contains("apikey")
        || normalized.contains("api_key")
        || normalized.contains("api-key");
  }

  private record InvocationExecution(
      ZalavaOperationResult response,
      long elapsedMillis,
      String responseJson,
      String failureMessage) {}

  private static ModuleEnablement.EnabledModule enabled(
      ModuleDevelopmentRequest request, LocalArtifactInspection.InspectedArtifact artifact) {
    return new ModuleEnablement.EnabledModule(
        request.currentRevision().contract().module().moduleId(),
        request.currentRevision().contract().module().versionPolicy(),
        artifact.path(),
        artifact.sha256Digest(),
        ">=1.0.0");
  }
}
