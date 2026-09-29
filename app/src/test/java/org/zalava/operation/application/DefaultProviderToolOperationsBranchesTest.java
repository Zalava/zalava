package org.zalava.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.in.ProviderToolOperationException;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.operation.application.port.out.ToolApprovalPort;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.JsonNode;

/**
 * Covers the defensive branches of the tool-operation use case: unknown providers/tools, malformed
 * arguments, provider failure classification, and approval decision error mapping.
 */
class DefaultProviderToolOperationsBranchesTest {

  private static final InvocationContext OPERATOR =
      new InvocationContext("operator-1", false, Map.of("source", "test"));

  private final FailingProvider provider = new FailingProvider();
  private final ProviderToolOperations operations =
      new DefaultProviderToolOperations(
          providerId ->
              "test-provider".equals(providerId) ? Optional.of(provider) : Optional.empty(),
          new ThrowingApprovals(),
          List.of());

  @Test
  void reportsMissingToolThroughTypedApplicationError() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "missing", "{}", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.TOOL_NOT_FOUND));
  }

  @Test
  void rejectsMalformedJsonArgumentsAsValidation() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "read", "not-json", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(ProviderToolOperationException.Code.VALIDATION);
              assertThat(exception).hasMessageContaining("must be valid JSON");
            });
  }

  @Test
  void rejectsNonObjectJsonArgumentsAsValidation() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "read", "[\"path\"]", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(ProviderToolOperationException.Code.VALIDATION);
              assertThat(exception).hasMessageContaining("must be a JSON object");
            });
  }

  @Test
  void mapsUnsupportedProviderFailuresToUnsupportedCode() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "unsupported", "{}", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.UNSUPPORTED));
  }

  @Test
  void mapsIllegalArgumentProviderFailuresToValidationCode() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "invalid", "{}", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.VALIDATION));
  }

  @Test
  void mapsUnknownApprovalRequestToApprovalNotFound() {
    assertThatThrownBy(() -> operations.allowUnscoped("request-missing"))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.APPROVAL_NOT_FOUND));
  }

  @Test
  void mapsConflictingApprovalDecisionToApprovalConflict() {
    assertThatThrownBy(() -> operations.denyUnscoped("request-conflict"))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.APPROVAL_CONFLICT));
  }

  private static final class ThrowingApprovals implements ToolApprovalPort {

    @Override
    public ToolApproval create(
        ZalavaProvider provider,
        ZalavaToolDescriptor tool,
        InvocationContext context,
        String argumentsJson,
        TaskReference taskReference) {
      throw new IllegalStateException("not expected in these tests");
    }

    @Override
    public Optional<ToolApproval> consumeDecision(
        TaskReference taskReference, String providerId, String toolName, String argumentsJson) {
      return Optional.empty();
    }

    @Override
    public Optional<ToolApproval> findUnscopedDecision(
        String actorId, String providerId, String toolName, String argumentsJson) {
      return Optional.empty();
    }

    @Override
    public Optional<ToolApproval> findAllowedToolPolicy(
        String actorId, String providerId, String toolName, Map<String, String> providerScope) {
      return Optional.empty();
    }

    @Override
    public ToolApproval allowUnscoped(String requestId) {
      throw new ApprovalNotFoundException(requestId);
    }

    @Override
    public ToolApproval allowUnscopedTool(String requestId) {
      throw new ApprovalConflictException("request-already-decided");
    }

    @Override
    public ToolApproval denyUnscoped(String requestId) {
      if ("request-conflict".equals(requestId)) {
        throw new ApprovalConflictException("request-already-decided");
      }
      throw new ApprovalNotFoundException(requestId);
    }
  }

  private static final class FailingProvider implements ZalavaProvider {

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "test-provider",
          "test-module",
          "test",
          "Test Provider",
          "Provider for branch tests.",
          "1.0.0",
          ProviderCapabilities.toolsOnly(),
          List.of("test"),
          Map.of("root", "workspace"));
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(
          new ZalavaToolDescriptor("read", "Reads.", false, null),
          new ZalavaToolDescriptor("unsupported", "Unsupported.", false, null),
          new ZalavaToolDescriptor("invalid", "Invalid.", false, null));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, JsonNode arguments, InvocationContext context) {
      if ("unsupported".equals(toolName)) {
        throw new UnsupportedOperationException("tool is not supported");
      }
      if ("invalid".equals(toolName)) {
        throw new IllegalArgumentException("arguments are invalid");
      }
      throw new IllegalStateException("not expected in these tests");
    }
  }
}
