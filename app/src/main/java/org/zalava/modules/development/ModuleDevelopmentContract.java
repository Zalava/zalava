package org.zalava.modules.development;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ModuleDevelopmentContract(
    Module module,
    String purpose,
    String targetZalavaApiVersion,
    List<Tool> tools,
    List<ExpectedError> expectedErrors,
    List<AcceptanceScenario> acceptanceScenarios,
    OperationalRequirements operationalRequirements,
    DeliveryRequirements deliveryRequirements) {

  private static final Pattern CONCRETE_SEMANTIC_VERSION =
      Pattern.compile(
          "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

  public ModuleDevelopmentContract {
    module = Objects.requireNonNull(module, "module must not be null");
    purpose = requiredText(purpose, "purpose");
    targetZalavaApiVersion = requiredText(targetZalavaApiVersion, "targetZalavaApiVersion");
    tools = immutableNonEmpty(tools, "tools");
    expectedErrors = expectedErrors == null ? List.of() : List.copyOf(expectedErrors);
    acceptanceScenarios = immutableNonEmpty(acceptanceScenarios, "acceptanceScenarios");
    operationalRequirements =
        Objects.requireNonNull(operationalRequirements, "operationalRequirements must not be null");
    deliveryRequirements =
        Objects.requireNonNull(deliveryRequirements, "deliveryRequirements must not be null");
    requireUniqueToolNames(tools);
  }

  public record Module(String moduleId, String versionPolicy) {
    public Module {
      moduleId = requiredText(moduleId, "moduleId");
      versionPolicy = requiredText(versionPolicy, "versionPolicy");
    }
  }

  public static void requireConcreteModuleVersion(ModuleDevelopmentContract contract) {
    Objects.requireNonNull(contract, "contract must not be null");
    if (!CONCRETE_SEMANTIC_VERSION.matcher(contract.module().versionPolicy()).matches()) {
      throw new IllegalArgumentException(
          "versionPolicy must be a concrete semantic version for the pilot");
    }
  }

  public static void requireConcreteApiVersion(String version) {
    if (version == null || !CONCRETE_SEMANTIC_VERSION.matcher(version).matches()) {
      throw new IllegalArgumentException("module API version must be a concrete semantic version");
    }
  }

  public record Tool(
      String name,
      String description,
      String inputSchema,
      String outputSchema,
      List<String> knownErrorCodes,
      List<ExampleCall> examples) {
    public Tool {
      name = requiredText(name, "tool.name");
      description = requiredText(description, "tool.description");
      inputSchema = requiredText(inputSchema, "tool.inputSchema");
      outputSchema = requiredText(outputSchema, "tool.outputSchema");
      knownErrorCodes = knownErrorCodes == null ? List.of() : List.copyOf(knownErrorCodes);
      examples = examples == null ? List.of() : List.copyOf(examples);
    }
  }

  public record ExampleCall(String inputJson, String expectedOutputJson) {
    public ExampleCall {
      inputJson = requiredText(inputJson, "example.inputJson");
      expectedOutputJson = requiredText(expectedOutputJson, "example.expectedOutputJson");
    }
  }

  public record ExpectedError(String code, String description) {
    public ExpectedError {
      code = requiredText(code, "expectedError.code");
      description = requiredText(description, "expectedError.description");
    }
  }

  public record AcceptanceScenario(
      String id, String requestJson, List<ResponseAssertion> assertions, String expectedErrorCode) {
    public AcceptanceScenario {
      id = requiredText(id, "acceptanceScenario.id");
      requestJson = requiredText(requestJson, "acceptanceScenario.requestJson");
      assertions = assertions == null ? List.of() : List.copyOf(assertions);
      if (assertions.isEmpty() && (expectedErrorCode == null || expectedErrorCode.isBlank())) {
        throw new IllegalArgumentException(
            "acceptanceScenario requires assertions or an expected error code");
      }
    }
  }

  public record ResponseAssertion(String path, String type, String expectedValueJson) {
    public ResponseAssertion {
      path = requiredText(path, "assertion.path");
      type = requiredText(type, "assertion.type");
      expectedValueJson = requiredText(expectedValueJson, "assertion.expectedValueJson");
    }
  }

  public record OperationalRequirements(
      Long timeoutMs,
      Long maximumResponseBytes,
      Boolean networkAccessRequired,
      List<String> namedSecretsRequired,
      Boolean filesystemAccessRequired,
      Boolean processExecutionRequired,
      String supportedRuntime) {
    public OperationalRequirements {
      if (timeoutMs != null && timeoutMs < 1)
        throw new IllegalArgumentException("timeoutMs must be positive");
      if (maximumResponseBytes != null && maximumResponseBytes < 1)
        throw new IllegalArgumentException("maximumResponseBytes must be positive");
      namedSecretsRequired =
          namedSecretsRequired == null ? List.of() : List.copyOf(namedSecretsRequired);
    }
  }

  public record DeliveryRequirements(
      String packageFormat,
      String fileNamePattern,
      String requiredManifestVersion,
      boolean multipleArtifactsAllowed,
      Map<String, String> requiredMetadata) {
    public DeliveryRequirements {
      packageFormat = requiredText(packageFormat, "delivery.packageFormat");
      fileNamePattern = requiredText(fileNamePattern, "delivery.fileNamePattern");
      requiredManifestVersion =
          requiredText(requiredManifestVersion, "delivery.requiredManifestVersion");
      requiredMetadata = requiredMetadata == null ? Map.of() : Map.copyOf(requiredMetadata);
    }
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " must not be blank");
    return value;
  }

  private static <T> List<T> immutableNonEmpty(List<T> values, String field) {
    if (values == null || values.isEmpty())
      throw new IllegalArgumentException(field + " must not be empty");
    return List.copyOf(values);
  }

  private static void requireUniqueToolNames(List<Tool> tools) {
    Set<String> names = new LinkedHashSet<>();
    for (Tool tool : tools)
      if (!names.add(tool.name())) throw new IllegalArgumentException("tool names must be unique");
  }
}
