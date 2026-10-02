package org.zalava.capabilities.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.out.ToolApprovalPort;
import tools.jackson.databind.JsonNode;

class MemberProviderCapabilityPolicyTest {
  @Test
  void permitsOnlyExplicitlyMarkedScopedMemberOperations() {
    TestProvider provider =
        new TestProvider(List.of("sea_backed"), List.of("member-safe"), Map.of("owner", "self"));
    DefaultProviderToolOperations operations = operations(provider);

    var outcome =
        operations.invoke(
            ProviderToolOperations.ToolInvocationCommand.operator(
                "test", "read", "{}", memberContext()));

    assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(provider.calls.get()).isOne();
    assertThat(provider.context.actorId()).isEqualTo("member-actor");
    assertThat(provider.context.attributes()).containsEntry("accountRole", "MEMBER");
  }

  @Test
  void refusesLegacyBroadAccessEvenWhenItClaimsTheMemberMarker() {
    TestProvider provider =
        new TestProvider(
            List.of("sea_backed", "broad-access"), List.of("member-safe"), Map.of("owner", "self"));
    DefaultProviderToolOperations operations = operations(provider);

    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test", "read", "{}", memberContext())))
        .isInstanceOf(ProviderToolOperationException.class)
        .extracting(value -> ((ProviderToolOperationException) value).code())
        .isEqualTo(ProviderToolOperationException.Code.UNSUPPORTED);
    assertThat(provider.calls.get()).isZero();
  }

  @Test
  void defaultsToDenyWithoutAnExplicitMarkerOrProviderScope() {
    TestProvider provider = new TestProvider(List.of("sea_backed"), List.of(), Map.of());

    assertThatThrownBy(
            () ->
                operations(provider)
                    .invoke(
                        ProviderToolOperations.ToolInvocationCommand.operator(
                            "test", "read", "{}", memberContext())))
        .isInstanceOf(ProviderToolOperationException.class)
        .hasMessageContaining("Member capability policy denied");
  }

  private static DefaultProviderToolOperations operations(TestProvider provider) {
    return new DefaultProviderToolOperations(
        providerId -> "test".equals(providerId) ? Optional.of(provider) : Optional.empty(),
        org.mockito.Mockito.mock(ToolApprovalPort.class),
        List.of(),
        new org.zalava.capabilities.operation.adapter.out.json.JacksonToolArgumentDecoder());
  }

  private static InvocationContext memberContext() {
    return new InvocationContext(
        "member-actor", false, Map.of("source", "product-chat", "accountRole", "MEMBER"));
  }

  private static final class TestProvider implements ZalavaProvider {
    private final List<String> providerTags;
    private final List<String> toolTags;
    private final Map<String, String> scope;
    private final AtomicInteger calls = new AtomicInteger();
    private InvocationContext context;

    private TestProvider(
        List<String> providerTags, List<String> toolTags, Map<String, String> scope) {
      this.providerTags = providerTags;
      this.toolTags = toolTags;
      this.scope = scope;
    }

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "test",
          "module",
          "test",
          "Test",
          "Test provider",
          "1",
          capabilities(),
          providerTags,
          scope);
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(new ZalavaToolDescriptor("read", "Read", false, toolTags));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName,
        java.util.Map<String, Object> argumentValues,
        InvocationContext invocationContext) {
      JsonNode arguments = new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
      calls.incrementAndGet();
      context = invocationContext;
      return ZalavaOperationResult.success(Map.of("status", "ok"));
    }
  }
}
