package org.zalava.capabilities.operation.adapter.in.http;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.capabilities.operation.application.model.ToolApproval;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.modules.runtime.SeaRuntime;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/sea")
@Profile({"dev", "test"})
public class ProviderOperationAdminController {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SeaRuntime seaRuntime;
  private final ProviderToolOperations providerToolOperations;

  public ProviderOperationAdminController(
      SeaRuntime seaRuntime, ProviderToolOperations providerToolOperations) {
    this.seaRuntime = seaRuntime;
    this.providerToolOperations = providerToolOperations;
  }

  @PostMapping("/providers/{providerId}/tools/{toolName}/invoke")
  public ResponseEntity<?> invokeTool(
      @PathVariable String providerId,
      @PathVariable String toolName,
      @RequestBody(required = false) ToolInvocationRequest request) {
    ToolInvocationRequest invocationRequest =
        request == null ? ToolInvocationRequest.empty() : request;
    ProviderToolOperations.ToolInvocationOutcome outcome =
        executeToolUseCase(
            () ->
                providerToolOperations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        providerId,
                        toolName,
                        invocationRequest.argumentsAsJsonString(),
                        invocationRequest.toInvocationContext())));
    if (outcome.status() == ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL) {
      return ResponseEntity.accepted().body(PermissionRequestResponse.from(outcome.approval()));
    }
    return ResponseEntity.ok(OperationResultResponse.from(outcome.result()));
  }

  @PostMapping("/permission-requests/{requestId}/allow")
  public OperationResultResponse allowPermissionRequest(@PathVariable String requestId) {
    return OperationResultResponse.from(
        executeToolUseCase(() -> providerToolOperations.allowUnscoped(requestId)).result());
  }

  @PostMapping("/permission-requests/{requestId}/allow-tool")
  public OperationResultResponse allowToolPermissionRequest(@PathVariable String requestId) {
    return OperationResultResponse.from(
        executeToolUseCase(() -> providerToolOperations.allowUnscopedTool(requestId)).result());
  }

  @PostMapping("/permission-requests/{requestId}/deny")
  public PermissionRequestResponse denyPermissionRequest(@PathVariable String requestId) {
    return PermissionRequestResponse.from(
        executeToolUseCase(() -> providerToolOperations.denyUnscoped(requestId)));
  }

  @PostMapping("/providers/{providerId}/resources/read")
  public OperationResultResponse readResource(
      @PathVariable String providerId, @RequestBody(required = false) ResourceReadRequest request) {
    ResourceReadRequest readRequest = request == null ? ResourceReadRequest.empty() : request;
    if (readRequest.uri() == null || readRequest.uri().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource uri is required");
    }
    return executeOperation(
        () ->
            findProvider(providerId)
                .readResource(readRequest.uri(), readRequest.toInvocationContext()));
  }

  @PostMapping("/providers/{providerId}/prompts/{promptName}/resolve")
  public OperationResultResponse resolvePrompt(
      @PathVariable String providerId,
      @PathVariable String promptName,
      @RequestBody(required = false) PromptResolutionRequest request) {
    PromptResolutionRequest resolutionRequest =
        request == null ? PromptResolutionRequest.empty() : request;
    return executeOperation(
        () ->
            findProvider(providerId)
                .resolvePrompt(
                    promptName,
                    resolutionRequest.argumentsAsMap(),
                    resolutionRequest.toInvocationContext()));
  }

  private ZalavaProvider findProvider(String providerId) {
    return seaRuntime
        .findLoadedProvider(providerId)
        .map(org.zalava.modules.runtime.LoadedSeaProvider::provider)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "SEA provider not found: " + providerId));
  }

  private static <T> T executeToolUseCase(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ProviderToolOperationException ex) {
      HttpStatus status =
          switch (ex.code()) {
            case PROVIDER_NOT_FOUND, TOOL_NOT_FOUND, APPROVAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case APPROVAL_CONFLICT -> HttpStatus.CONFLICT;
            case UNSUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case EXECUTION -> HttpStatus.INTERNAL_SERVER_ERROR;
          };
      throw new ResponseStatusException(status, ex.getMessage(), ex);
    }
  }

  private OperationResultResponse executeOperation(Supplier<ZalavaOperationResult> operation) {
    try {
      return OperationResultResponse.from(operation.get());
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (UnsupportedOperationException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), ex);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "SEA operation failed", ex);
    }
  }

  public record ToolInvocationRequest(
      String actorId,
      Boolean confirmed,
      Map<String, String> attributes,
      Map<String, Object> arguments) {
    static ToolInvocationRequest empty() {
      return new ToolInvocationRequest(null, false, Map.of(), Map.of());
    }

    InvocationContext toInvocationContext() {
      return invocationContext(actorId, confirmed, attributes);
    }

    String argumentsAsJsonString() {
      return JSON.valueToTree(arguments == null ? Map.of() : arguments).toString();
    }
  }

  public record ResourceReadRequest(
      String actorId, Boolean confirmed, Map<String, String> attributes, String uri) {
    static ResourceReadRequest empty() {
      return new ResourceReadRequest(null, false, Map.of(), null);
    }

    InvocationContext toInvocationContext() {
      return invocationContext(actorId, confirmed, attributes);
    }
  }

  public record PromptResolutionRequest(
      String actorId,
      Boolean confirmed,
      Map<String, String> attributes,
      Map<String, Object> arguments) {
    static PromptResolutionRequest empty() {
      return new PromptResolutionRequest(null, false, Map.of(), Map.of());
    }

    InvocationContext toInvocationContext() {
      return invocationContext(actorId, confirmed, attributes);
    }

    Map<String, Object> argumentsAsMap() {
      return org.zalava.api.JsonArguments.immutable(arguments == null ? Map.of() : arguments);
    }
  }

  public record OperationResultResponse(
      boolean success, Object content, Map<String, Object> metadata) {
    static OperationResultResponse from(ZalavaOperationResult result) {
      return new OperationResultResponse(result.success(), result.content(), result.metadata());
    }
  }

  public record PermissionRequestResponse(
      String requestId, String decision, String providerId, String toolName) {
    static PermissionRequestResponse from(ToolApproval request) {
      return new PermissionRequestResponse(
          request.requestId(),
          request.decision().name().toLowerCase(),
          request.providerId(),
          request.toolName());
    }
  }

  private static InvocationContext invocationContext(
      String actorId, Boolean confirmed, Map<String, String> attributes) {
    return new InvocationContext(
        actorId == null || actorId.isBlank() ? "operator" : actorId,
        Boolean.TRUE.equals(confirmed),
        attributes == null ? Map.of() : attributes);
  }
}
