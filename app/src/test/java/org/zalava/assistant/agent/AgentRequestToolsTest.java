package org.zalava.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackCatalog;
import org.zalava.capabilities.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;

class AgentRequestToolsTest {

  private final Object taskTool = new Object();
  private final Object mcpTools = new Object();
  private final ToolDiscovery discovery = mock(ToolDiscovery.class);
  private final ToolCallback readCallback = mock(ToolCallback.class);
  private final ToolCallback writeCallback = mock(ToolCallback.class);
  private final ZalavaToolCallbackCatalog callbackCatalog = mock(ZalavaToolCallbackCatalog.class);
  private final AgentRequestTools requestTools =
      new AgentRequestTools(List.of(taskTool, mcpTools), discovery, callbackCatalog);

  @Test
  void preservesBootstrapToolsAndAddsMatchingZalavaCallbacks() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    ToolDiscovery.ToolMatch write = match("files", "write");
    when(discovery.search("read and update notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read, write));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);
    when(callbackCatalog.callback("files", "write")).thenReturn(writeCallback);

    assertThat(requestTools.forInput("read and update notes"))
        .containsExactly(taskTool, mcpTools, readCallback, writeCallback);
  }

  @Test
  void resolvesLoadedZalavaCallbacksForEachChatRequest() {
    ToolCallback timeCallback = mock(ToolCallback.class);
    when(callbackCatalog.callbacks()).thenReturn(List.of(timeCallback));
    when(discovery.search("hello", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    assertThat(
            new AgentRequestTools(List.of(taskTool, mcpTools), discovery, callbackCatalog)
                .forInput("hello"))
        .containsExactly(taskTool, mcpTools, timeCallback);
    verify(callbackCatalog).callbacks();
  }

  @Test
  void returnsCompactSummariesForSelectedZalavaCallbacks() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);
    when(discovery.load("files", "read")).thenReturn(definition("files", "read"));

    AgentRequestTools.RequestToolSelection selection =
        requestTools.resolve("conversation-1", "read notes");

    assertThat(selection.tools()).containsExactly(taskTool, mcpTools, readCallback);
    assertThat(selection.toolSummaries())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.providerId()).isEqualTo("files");
              assertThat(summary.toolName()).isEqualTo("read");
              assertThat(summary.description()).isEqualTo("read files");
              assertThat(summary.policyTags()).contains("zalava_backed");
            });
    assertThat(selection.toolDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.providerId()).isEqualTo("files");
              assertThat(definition.toolName()).isEqualTo("read");
              assertThat(definition.available()).isTrue();
              assertThat(definition.inputSchema()).containsEntry("type", "object");
            });
  }

  @Test
  void keepsDefinitionUnavailableWhenSelectedToolCannotBeLoaded() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);
    when(discovery.load("files", "read"))
        .thenThrow(new java.util.NoSuchElementException("missing"));

    AgentRequestTools.RequestToolSelection selection =
        requestTools.resolve("conversation-1", "read notes");

    assertThat(selection.tools()).containsExactly(taskTool, mcpTools, readCallback);
    assertThat(selection.toolDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.available()).isFalse();
              assertThat(definition.inputSchema()).containsEntry("status", "unavailable");
            });
  }

  @Test
  void reusesActivatedCallbacksForConversation() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(discovery.search("follow up", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(requestTools.forInput("conversation-1", "read notes"))
        .containsExactly(taskTool, mcpTools, readCallback);
    assertThat(requestTools.forInput("conversation-1", "follow up"))
        .containsExactly(taskTool, mcpTools, readCallback);

    verify(discovery).search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
    verify(discovery).search("follow up", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
  }

  @Test
  void keepsActivatedCallbacksScopedToConversation() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(discovery.search("follow up", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(requestTools.forInput("conversation-1", "read notes"))
        .containsExactly(taskTool, mcpTools, readCallback);
    assertThat(requestTools.forInput("conversation-2", "follow up"))
        .containsExactly(taskTool, mcpTools);

    verify(discovery).search("follow up", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
  }

  @Test
  void keepsOnlyBootstrapToolsWhenDiscoveryHasNoMatches() {
    when(discovery.search("answer from context", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    assertThat(requestTools.forInput("answer from context")).containsExactly(taskTool, mcpTools);
  }

  @Test
  void addsCapabilityGapRecommendationOnlyWhenDiscoveryHasNoMatches() {
    Object recommendation = new Object();
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools), discovery, callbackCatalog, List.of(recommendation));
    when(discovery.search("forecast pollen", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    AgentRequestTools.RequestToolSelection selection =
        tools.resolve("conversation-1", "forecast pollen");

    assertThat(selection.tools()).containsExactly(taskTool, mcpTools, recommendation);
    assertThat(selection.toolSummaries()).isEmpty();
    assertThat(selection.toolDefinitions()).isEmpty();
  }

  @Test
  void doesNotAddCapabilityGapRecommendationWhenDiscoveryFindsAProviderTool() {
    Object recommendation = new Object();
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools), discovery, callbackCatalog, List.of(recommendation));
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(tools.forInput("read notes")).containsExactly(taskTool, mcpTools, readCallback);
  }

  @Test
  void invokesRemoteDiscoveryOnlyWhenInstalledDiscoveryHasNoMatch() {
    RemoteCapabilityDiscovery remoteDiscovery = mock(RemoteCapabilityDiscovery.class);
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools), discovery, callbackCatalog, List.of(), remoteDiscovery);
    when(discovery.search("forecast pollen", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    tools.resolve("conversation-1", "forecast pollen");

    verify(remoteDiscovery).discover("forecast pollen", 0);
  }

  @Test
  void installedMatchSuppressesRemoteDiscoveryEntirely() {
    RemoteCapabilityDiscovery remoteDiscovery = mock(RemoteCapabilityDiscovery.class);
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools), discovery, callbackCatalog, List.of(), remoteDiscovery);
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(tools.forInput("read notes")).containsExactly(taskTool, mcpTools, readCallback);
    verify(remoteDiscovery, never()).discover(any(), anyInt());
  }

  @Test
  void remoteDiscoveryFailureNeverBreaksToolSelection() {
    RemoteCapabilityDiscovery remoteDiscovery = mock(RemoteCapabilityDiscovery.class);
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools), discovery, callbackCatalog, List.of(), remoteDiscovery);
    when(discovery.search("forecast pollen", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());
    when(remoteDiscovery.discover("forecast pollen", 0))
        .thenThrow(new IllegalStateException("evidence store unavailable"));

    assertThat(tools.forInput("forecast pollen")).containsExactly(taskTool, mcpTools);
  }

  @Test
  void keepsOnlyBootstrapToolsForBlankInput() {
    assertThat(requestTools.forInput(" ")).containsExactly(taskTool, mcpTools);

    verify(discovery, never()).search(" ", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
  }

  @Test
  void treatsBlankConversationIdAsStateless() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(discovery.search("follow up", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(requestTools.forInput(" ", "read notes"))
        .containsExactly(taskTool, mcpTools, readCallback);
    assertThat(requestTools.forInput(" ", "follow up")).containsExactly(taskTool, mcpTools);
  }

  @Test
  void doesNotDuplicateCallbackAlreadyPresentInBootstrapTools() {
    ToolDiscovery.ToolMatch read = match("files", "read");
    AgentRequestTools tools =
        new AgentRequestTools(List.of(taskTool, readCallback), discovery, callbackCatalog);
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(tools.forInput("read notes")).containsExactly(taskTool, readCallback);
  }

  @Test
  void usesBoundedDiscoveryForEachRequest() {
    when(discovery.search("first", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());
    when(discovery.search("second", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    requestTools.forInput("first");
    requestTools.forInput("second");

    verify(discovery).search("first", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
    verify(discovery).search("second", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES);
  }

  @Test
  void limitsCallbacksEvenWhenDiscoveryReturnsTooManyMatches() {
    List<ToolDiscovery.ToolMatch> matches =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(index -> match("provider", "tool-" + index))
            .toList();
    List<ToolCallback> callbacks =
        java.util.stream.IntStream.range(0, 6).mapToObj(index -> mock(ToolCallback.class)).toList();
    when(discovery.search("many", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(matches);
    for (int i = 0; i < callbacks.size(); i++) {
      when(callbackCatalog.callback("provider", "tool-" + i)).thenReturn(callbacks.get(i));
    }

    assertThat(requestTools.forInput("many"))
        .containsExactlyElementsOf(
            java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(taskTool, mcpTools),
                    callbacks.stream().limit(AgentRequestTools.MAX_ZALAVA_TOOLS))
                .toList());
  }

  @Test
  void limitsCallbacksAcrossPersistedAndNewMatches() {
    List<ToolDiscovery.ToolMatch> firstMatches =
        java.util.stream.IntStream.range(0, 4)
            .mapToObj(index -> match("provider", "persisted-" + index))
            .toList();
    ToolDiscovery.ToolMatch newMatch = match("provider", "new");
    ToolDiscovery.ToolMatch extraMatch = match("provider", "extra");
    List<ToolCallback> callbacks =
        java.util.stream.IntStream.range(0, 6).mapToObj(index -> mock(ToolCallback.class)).toList();
    when(discovery.search("first", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(firstMatches);
    when(discovery.search("second", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(newMatch, extraMatch));
    for (int i = 0; i < firstMatches.size(); i++) {
      when(callbackCatalog.callback("provider", "persisted-" + i)).thenReturn(callbacks.get(i));
    }
    when(callbackCatalog.callback("provider", "new")).thenReturn(callbacks.get(4));

    assertThat(requestTools.forInput("conversation-1", "first")).hasSize(2 + firstMatches.size());
    assertThat(requestTools.forInput("conversation-1", "second"))
        .containsExactly(
            taskTool,
            mcpTools,
            callbacks.get(0),
            callbacks.get(1),
            callbacks.get(2),
            callbacks.get(3),
            callbacks.get(4));
    verify(callbackCatalog, never()).callback("provider", "extra");
  }

  @Test
  void skipsMatchesWithoutZalavaBackedTrustMetadata() {
    ToolDiscovery.ToolMatch untrusted =
        match("files", "read", false, List.of("filesystem"), List.of("filesystem"));
    when(discovery.search("read notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(untrusted));

    assertThat(requestTools.forInput("read notes")).containsExactly(taskTool, mcpTools);
    verify(callbackCatalog, never()).callback("files", "read");
  }

  @Test
  void skipsMatchesWithBlockedPermissionTags() {
    ToolDiscovery.ToolMatch blocked =
        match(
            "host-shell",
            "execute",
            true,
            List.of("zalava_backed", "shell", "broad-access"),
            List.of("zalava_backed"));
    when(discovery.search("run command", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(blocked));

    assertThat(requestTools.forInput("run command")).containsExactly(taskTool, mcpTools);
    verify(callbackCatalog, never()).callback("host-shell", "execute");
  }

  @Test
  void reappliesPolicyBeforeUsingStoredActivation() {
    SessionToolActivations sessionActivations =
        new SessionToolActivations(AgentRequestTools.MAX_ZALAVA_TOOLS);
    sessionActivations.activate(
        "conversation-1",
        List.of(
            match(
                "host-shell",
                "execute",
                true,
                List.of("zalava_backed", "shell"),
                List.of("zalava_backed"))));
    AgentRequestTools tools =
        new AgentRequestTools(
            List.of(taskTool, mcpTools),
            discovery,
            callbackCatalog,
            new DynamicToolActivationPolicy(),
            sessionActivations);
    when(discovery.search("continue", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of());

    assertThat(tools.forInput("conversation-1", "continue")).containsExactly(taskTool, mcpTools);
    verify(callbackCatalog, never()).callback("host-shell", "execute");
  }

  @Test
  void keepsScanningCandidatesAfterPolicyRejections() {
    ToolDiscovery.ToolMatch blocked =
        match(
            "host-shell",
            "execute",
            true,
            List.of("zalava_backed", "shell"),
            List.of("zalava_backed"));
    ToolDiscovery.ToolMatch read = match("files", "read");
    when(discovery.search("read after blocked", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(List.of(blocked, read));
    when(callbackCatalog.callback("files", "read")).thenReturn(readCallback);

    assertThat(requestTools.forInput("read after blocked"))
        .containsExactly(taskTool, mcpTools, readCallback);
  }

  @Test
  void recordsApprovalAndAuditBoundariesForSideEffectingMatches() {
    DynamicToolActivationPolicy.ActivationDecision decision =
        new DynamicToolActivationPolicy().evaluate(match("browser", "navigateTo", true));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.notes())
        .contains(
            "approval boundary: side-effecting calls require execution approval",
            "audit boundary: ProviderToolOperations observation");
  }

  @Test
  void policyFilteredSearchRecoversARelevantToolThatDeterministicPreselectionDropped() {
    List<ToolDiscovery.ToolMatch> corpus = new java.util.ArrayList<>();
    for (int index = 0; index < 5; index++) {
      corpus.add(
          matchWithDescription("provider", "apple" + index, "Handles apples number " + index));
    }
    corpus.add(matchWithDescription("files", "readNotes", "Read notes from the workspace."));
    when(discovery.search("notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(corpus);
    when(callbackCatalog.callback("files", "readNotes")).thenReturn(readCallback);
    for (int index = 0; index < 5; index++) {
      when(callbackCatalog.callback("provider", "apple" + index))
          .thenReturn(mock(ToolCallback.class));
    }
    AgentRequestTools searched =
        new AgentRequestTools(
            List.of(taskTool, mcpTools),
            discovery,
            callbackCatalog,
            List.of(),
            RemoteCapabilityDiscovery.noop(),
            new PolicyFilteredToolSearch(
                new org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolIndex()));

    AgentRequestTools.RequestToolSelection selection = searched.resolve("conversation-1", "notes");

    assertThat(selection.toolSummaries())
        .extracting(AgentRequestTools.ToolSummary::toolName)
        .containsExactly("readNotes");
  }

  @Test
  void fallsBackToDeterministicPreselectionWhenSearchFindsNothing() {
    List<ToolDiscovery.ToolMatch> corpus =
        List.of(
            matchWithDescription("provider", "apple", "Handles apples."),
            matchWithDescription("files", "readNotes", "Read notes from the workspace."));
    when(discovery.search("unmatched phrase", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(corpus);
    ToolCallback apple = mock(ToolCallback.class);
    when(callbackCatalog.callback("provider", "apple")).thenReturn(apple);
    when(callbackCatalog.callback("files", "readNotes")).thenReturn(readCallback);
    AgentRequestTools deterministic =
        new AgentRequestTools(List.of(taskTool), discovery, callbackCatalog);
    AgentRequestTools searched =
        new AgentRequestTools(
            List.of(taskTool),
            discovery,
            callbackCatalog,
            List.of(),
            RemoteCapabilityDiscovery.noop(),
            new PolicyFilteredToolSearch(
                new org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolIndex()));

    AgentRequestTools.RequestToolSelection expected =
        deterministic.resolve("conversation-1", "unmatched phrase");
    AgentRequestTools.RequestToolSelection actual =
        searched.resolve("conversation-1", "unmatched phrase");

    assertThat(actual.toolSummaries()).isEqualTo(expected.toolSummaries());
    assertThat(actual.tools()).isEqualTo(expected.tools());
  }

  @Test
  void policyFilteredSearchStillHonorsTheBoundedSelectionLimit() {
    List<ToolDiscovery.ToolMatch> corpus =
        java.util.stream.IntStream.range(0, 8)
            .mapToObj(
                index -> matchWithDescription("provider", "read" + index, "Read notes " + index))
            .toList();
    when(discovery.search("notes", AgentRequestTools.MAX_ZALAVA_TOOL_CANDIDATES))
        .thenReturn(corpus);
    for (int index = 0; index < 8; index++) {
      when(callbackCatalog.callback("provider", "read" + index))
          .thenReturn(mock(ToolCallback.class));
    }
    AgentRequestTools searched =
        new AgentRequestTools(
            List.of(taskTool),
            discovery,
            callbackCatalog,
            List.of(),
            RemoteCapabilityDiscovery.noop(),
            new PolicyFilteredToolSearch(
                new org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolIndex()));

    AgentRequestTools.RequestToolSelection selection = searched.resolve("conversation-1", "notes");

    assertThat(selection.toolSummaries()).hasSize(AgentRequestTools.MAX_ZALAVA_TOOLS);
    assertThat(selection.tools()).hasSize(1 + AgentRequestTools.MAX_ZALAVA_TOOLS);
  }

  private static ToolDiscovery.ToolMatch matchWithDescription(
      String providerId, String toolName, String description) {
    return new ToolDiscovery.ToolMatch(
        providerId,
        "Files",
        toolName,
        description,
        false,
        List.of("zalava_backed", "filesystem"),
        List.of("zalava_backed", "filesystem"),
        Map.of());
  }

  private static ToolDiscovery.ToolMatch match(String providerId, String toolName) {
    return match(providerId, toolName, false);
  }

  private static ToolDiscovery.ToolMatch match(
      String providerId, String toolName, boolean sideEffecting) {
    return match(
        providerId,
        toolName,
        sideEffecting,
        List.of("zalava_backed", "filesystem"),
        List.of("zalava_backed", "filesystem"));
  }

  private static ToolDiscovery.ToolMatch match(
      String providerId,
      String toolName,
      boolean sideEffecting,
      List<String> policyTags,
      List<String> providerPolicyTags) {
    return new ToolDiscovery.ToolMatch(
        providerId,
        "Files",
        toolName,
        toolName + " files",
        sideEffecting,
        policyTags,
        providerPolicyTags,
        Map.of());
  }

  private static ToolDiscovery.ToolDefinition definition(String providerId, String toolName) {
    return new ToolDiscovery.ToolDefinition(
        providerId,
        "Files",
        toolName,
        toolName + " files",
        false,
        List.of("zalava_backed", "filesystem"),
        List.of("zalava_backed", "filesystem"),
        Map.of(),
        Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))));
  }
}
