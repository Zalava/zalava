package org.zalava.operation.adapter.in.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.discovery.application.DefaultInstalledToolDiscovery;
import org.zalava.operation.adapter.out.approval.SeaToolApprovalAdapter;
import org.zalava.operation.adapter.out.runtime.SeaRuntimeProviderCatalog;
import org.zalava.operation.application.DefaultProviderToolOperations;
import org.zalava.runtime.DefaultSeaRuntime;
import org.zalava.runtime.StaticSeaModuleRegistry;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.JsonNode;

class SeaProviderToolTest {

  private final MutableProvider provider = new MutableProvider();
  private final SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
  private final TaskExecutionContext taskExecutionContext = new TaskExecutionContext();
  private final SeaRuntimeProviderCatalog providerCatalog =
      new SeaRuntimeProviderCatalog(
          new DefaultSeaRuntime(
              new StaticSeaModuleRegistry(Set.of(new SingleProviderModule(provider))),
              ProviderFactoryContext.empty()));
  private final SeaProviderTool tool =
      new SeaProviderTool(
          new DefaultProviderToolOperations(
              providerCatalog, new SeaToolApprovalAdapter(approvals), List.of()),
          new DefaultInstalledToolDiscovery(providerCatalog),
          taskExecutionContext);

  @Test
  void searchesInstalledProviderToolsBeforeInvocation() {
    assertThat(tool.searchSeaProviderTools("filesystem write", 5))
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.providerId()).isEqualTo("mutable-provider");
              assertThat(match.toolName()).isEqualTo("write");
              assertThat(match.sideEffecting()).isTrue();
              assertThat(match.policyTags()).containsExactly("filesystem");
              assertThat(match.scope()).containsEntry("scope", "test");
            });
  }

  @Test
  void defaultsSearchResultLimitWhenTheModelOmitsIt() {
    assertThat(tool.searchSeaProviderTools("filesystem write", null))
        .singleElement()
        .satisfies(match -> assertThat(match.toolName()).isEqualTo("write"));
  }

  @Test
  void loadsSelectedProviderToolDefinitionBeforeInvocation() {
    assertThat(tool.loadSeaProviderTool("mutable-provider", "write"))
        .satisfies(
            definition -> {
              assertThat(definition.providerId()).isEqualTo("mutable-provider");
              assertThat(definition.toolName()).isEqualTo("write");
              assertThat(definition.inputSchema())
                  .containsEntry("type", "object")
                  .containsEntry("required", List.of("path"));
            });
  }

  @Test
  void createsJobScopedApprovalForSideEffectingToolCall() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");

    ZalavaOperationResult result =
        taskExecutionContext.call(
            reference,
            () ->
                tool.invokeSeaProviderTool(
                    "mutable-provider", "write", "{\"path\":\"notes/a.txt\"}"));

    assertThat(result.success()).isFalse();
    assertThat(provider.calls).hasValue(0);
    assertThat(approvals.pendingFor(reference))
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.taskReference()).isEqualTo(reference.path());
              assertThat(request.providerId()).isEqualTo("mutable-provider");
              assertThat(request.toolName()).isEqualTo("write");
            });
  }

  @Test
  void consumesApprovedJobScopedApprovalOnNextMatchingToolCall() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    taskExecutionContext.call(
        reference,
        () ->
            tool.invokeSeaProviderTool("mutable-provider", "write", "{\"path\":\"notes/a.txt\"}"));
    String requestId = approvals.pendingFor(reference).getFirst().requestId();
    approvals.allow(requestId, reference);

    ZalavaOperationResult result =
        taskExecutionContext.call(
            reference,
            () ->
                tool.invokeSeaProviderTool(
                    "mutable-provider", "write", "{\"path\":\"notes/a.txt\"}"));

    assertThat(result.success()).isTrue();
    assertThat(provider.calls).hasValue(1);
    assertThat(approvals.recentEntries().getFirst().consumedAt()).isNotNull();
  }

  @Test
  void consumesDeniedApprovalWithoutCallingProvider() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    taskExecutionContext.call(
        reference,
        () ->
            tool.invokeSeaProviderTool("mutable-provider", "write", "{\"path\":\"notes/a.txt\"}"));
    String requestId = approvals.pendingFor(reference).getFirst().requestId();
    approvals.deny(requestId, reference);

    ZalavaOperationResult result =
        taskExecutionContext.call(
            reference,
            () ->
                tool.invokeSeaProviderTool(
                    "mutable-provider", "write", "{\"path\":\"notes/a.txt\"}"));

    assertThat(result.success()).isFalse();
    assertThat(result.content())
        .isEqualTo(Map.of("status", "denied", "approvalRequestId", requestId));
    assertThat(provider.calls).hasValue(0);
    assertThat(approvals.recentEntries().getFirst().consumedAt()).isNotNull();
  }

  @Test
  void doesNotExecuteSideEffectingToolOutsideTrustedTaskContext() {
    ZalavaOperationResult result =
        tool.invokeSeaProviderTool("mutable-provider", "write", "{\"path\":\"notes/a.txt\"}");

    assertThat(result.success()).isFalse();
    assertThat(provider.calls).hasValue(0);
    assertThat(approvals.recentEntries())
        .singleElement()
        .satisfies(request -> assertThat(request.taskReference()).isNull());
  }

  @Test
  void keepsInvalidAgentArgumentsAsIllegalArgumentException() {
    assertThatThrownBy(() -> tool.invokeSeaProviderTool("mutable-provider", "write", "not-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEA provider tool arguments must be valid JSON");
  }

  private static final class MutableProvider implements ZalavaProvider {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "mutable-provider",
          "test-module",
          "mutable",
          "Mutable Provider",
          "Provider with a side-effecting test tool.",
          "1.0.0",
          capabilities(),
          List.of("test"),
          Map.of("scope", "test"));
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(
          new ZalavaToolDescriptor(
              "write",
              "Writes test content.",
              true,
              List.of("filesystem"),
              Map.of(
                  "type", "object",
                  "required", List.of("path"),
                  "properties", Map.of("path", Map.of("type", "string")))));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, JsonNode arguments, org.zalava.InvocationContext context) {
      calls.incrementAndGet();
      return ZalavaOperationResult.success(
          Map.of(
              "path", arguments.path("path").stringValue(""),
              "confirmed", context.confirmed()));
    }
  }

  private record SingleProviderModule(ZalavaProvider provider) implements ZalavaModule {

    @Override
    public org.zalava.ModuleDescriptor descriptor() {
      return new org.zalava.ModuleDescriptor(
          "test-module", "1.0.0", "Test Module", "Module for approval bridge tests.");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of(
          new ProviderFactory() {
            @Override
            public ProviderFactoryDescriptor descriptor() {
              return new ProviderFactoryDescriptor(
                  "test-factory",
                  "test-module",
                  "mutable-provider",
                  "Test Factory",
                  "Factory for approval bridge tests.");
            }

            @Override
            public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
              return List.of(provider);
            }
          });
    }
  }
}
