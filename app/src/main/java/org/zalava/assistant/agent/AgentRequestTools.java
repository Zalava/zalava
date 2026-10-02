package org.zalava.assistant.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.zalava.capabilities.discovery.adapter.out.springai.SeaToolCallbackCatalog;
import org.zalava.capabilities.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.identity.accounts.domain.AccountRole;

public final class AgentRequestTools {

  public static final int MAX_SEA_TOOLS = 5;
  static final int MAX_SEA_TOOL_CANDIDATES = 20;

  private final List<Object> bootstrapTools;
  private final ToolDiscovery toolDiscovery;
  private final SeaToolCallbackCatalog callbackCatalog;
  private final DynamicToolActivationPolicy activationPolicy;
  private final SessionToolActivations sessionActivations;
  private final List<Object> noMatchTools;
  private final RemoteCapabilityDiscovery remoteDiscovery;
  private final PolicyFilteredToolSearch toolSearch;

  public AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        new DynamicToolActivationPolicy(),
        new SessionToolActivations(MAX_SEA_TOOLS),
        List.of());
  }

  public AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      List<Object> noMatchTools) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        new DynamicToolActivationPolicy(),
        new SessionToolActivations(MAX_SEA_TOOLS),
        noMatchTools);
  }

  public AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      List<Object> noMatchTools,
      RemoteCapabilityDiscovery remoteDiscovery) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        new DynamicToolActivationPolicy(),
        new SessionToolActivations(MAX_SEA_TOOLS),
        noMatchTools,
        remoteDiscovery,
        new PolicyFilteredToolSearch(null, new DynamicToolActivationPolicy(), false));
  }

  public AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      List<Object> noMatchTools,
      RemoteCapabilityDiscovery remoteDiscovery,
      PolicyFilteredToolSearch toolSearch) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        new DynamicToolActivationPolicy(),
        new SessionToolActivations(MAX_SEA_TOOLS),
        noMatchTools,
        remoteDiscovery,
        toolSearch);
  }

  AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      DynamicToolActivationPolicy activationPolicy) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        activationPolicy,
        new SessionToolActivations(MAX_SEA_TOOLS),
        List.of());
  }

  AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      DynamicToolActivationPolicy activationPolicy,
      SessionToolActivations sessionActivations) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        activationPolicy,
        sessionActivations,
        List.of());
  }

  AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      DynamicToolActivationPolicy activationPolicy,
      SessionToolActivations sessionActivations,
      List<Object> noMatchTools) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        activationPolicy,
        sessionActivations,
        noMatchTools,
        RemoteCapabilityDiscovery.noop());
  }

  AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      DynamicToolActivationPolicy activationPolicy,
      SessionToolActivations sessionActivations,
      List<Object> noMatchTools,
      RemoteCapabilityDiscovery remoteDiscovery) {
    this(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        activationPolicy,
        sessionActivations,
        noMatchTools,
        remoteDiscovery,
        new PolicyFilteredToolSearch(null, activationPolicy, false));
  }

  AgentRequestTools(
      List<Object> bootstrapTools,
      ToolDiscovery toolDiscovery,
      SeaToolCallbackCatalog callbackCatalog,
      DynamicToolActivationPolicy activationPolicy,
      SessionToolActivations sessionActivations,
      List<Object> noMatchTools,
      RemoteCapabilityDiscovery remoteDiscovery,
      PolicyFilteredToolSearch toolSearch) {
    this.toolDiscovery = toolDiscovery;
    this.callbackCatalog = callbackCatalog;
    this.bootstrapTools = List.copyOf(bootstrapTools);
    this.activationPolicy = activationPolicy;
    this.sessionActivations = sessionActivations;
    this.noMatchTools = List.copyOf(noMatchTools);
    this.remoteDiscovery =
        remoteDiscovery == null ? RemoteCapabilityDiscovery.noop() : remoteDiscovery;
    this.toolSearch =
        toolSearch == null
            ? new PolicyFilteredToolSearch(null, activationPolicy, false)
            : toolSearch;
  }

  public List<Object> bootstrapTools() {
    return bootstrapTools;
  }

  public List<Object> forInput(String input) {
    return forInput(null, input);
  }

  public List<Object> forInput(String conversationId, String input) {
    return resolve(conversationId, input).tools();
  }

  public RequestToolSelection resolve(String conversationId, String input) {
    return resolve(conversationId, input, null);
  }

  public RequestToolSelection resolve(
      String conversationId, String input, AccountRole accountRole) {
    if (input == null || input.isBlank()) {
      return new RequestToolSelection(bootstrapTools, List.of(), List.of());
    }
    List<Object> requestTools = new ArrayList<>(bootstrapTools);
    callbackCatalog.entries().stream()
        .map(SeaToolCallbackCatalog.Entry::callback)
        .filter(callback -> !requestTools.contains(callback))
        .forEach(requestTools::add);
    List<ToolDiscovery.ToolMatch> newlyActivated = new ArrayList<>();
    List<ToolDiscovery.ToolMatch> selectedMatches = new ArrayList<>();
    Set<ToolKey> selectedToolKeys = new LinkedHashSet<>();
    if (hasText(conversationId)) {
      sessionActivations.activatedTools(conversationId).stream()
          .filter(match -> activationPolicy.allows(match, accountRole))
          .filter(match -> selectedToolKeys.add(ToolKey.from(match)))
          .limit(MAX_SEA_TOOLS)
          .peek(selectedMatches::add)
          .map(match -> callbackCatalog.callback(match.providerId(), match.toolName()))
          .filter(callback -> !requestTools.contains(callback))
          .forEach(requestTools::add);
    }
    int remainingToolSlots = Math.max(0, MAX_SEA_TOOLS - selectedToolKeys.size());
    List<ToolDiscovery.ToolMatch> discoveredMatches =
        toolDiscovery.search(input, MAX_SEA_TOOL_CANDIDATES);
    List<ToolDiscovery.ToolMatch> selectionCandidates = discoveredMatches;
    if (remainingToolSlots > 0 && hasText(conversationId) && toolSearch.enabled()) {
      List<ToolDiscovery.ToolMatch> searched =
          toolSearch.select(
              conversationId, input, discoveredMatches, accountRole, remainingToolSlots);
      if (!searched.isEmpty()) {
        selectionCandidates = searched;
      }
    }
    if (remainingToolSlots > 0) {
      selectionCandidates.stream()
          .filter(match -> activationPolicy.allows(match, accountRole))
          .filter(match -> selectedToolKeys.add(ToolKey.from(match)))
          .limit(remainingToolSlots)
          .peek(selectedMatches::add)
          .peek(newlyActivated::add)
          .map(match -> callbackCatalog.callback(match.providerId(), match.toolName()))
          .filter(callback -> !requestTools.contains(callback))
          .forEach(requestTools::add);
    }
    if (discoveredMatches.isEmpty()) {
      requestTools.addAll(noMatchTools);
      recordRemoteGapBestEffort(input, selectedMatches.size());
    }
    if (hasText(conversationId)) {
      sessionActivations.activate(conversationId, newlyActivated);
    }
    return new RequestToolSelection(
        requestTools,
        selectedMatches.stream().map(ToolSummary::from).toList(),
        selectedMatches.stream().map(this::definition).toList());
  }

  private void recordRemoteGapBestEffort(String input, int eligibleInstalledMatches) {
    try {
      remoteDiscovery.discover(input, eligibleInstalledMatches);
    } catch (RuntimeException ignored) {
    }
  }

  private ToolDefinitionSummary definition(ToolDiscovery.ToolMatch match) {
    try {
      ToolDiscovery.ToolDefinition definition =
          toolDiscovery.load(match.providerId(), match.toolName());
      return definition == null
          ? ToolDefinitionSummary.unavailable(match)
          : ToolDefinitionSummary.from(definition);
    } catch (NoSuchElementException | IllegalArgumentException ex) {
      return ToolDefinitionSummary.unavailable(match);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record ToolKey(String providerId, String toolName) {
    static ToolKey from(ToolDiscovery.ToolMatch match) {
      return new ToolKey(match.providerId(), match.toolName());
    }
  }

  public record RequestToolSelection(
      List<Object> tools,
      List<ToolSummary> toolSummaries,
      List<ToolDefinitionSummary> toolDefinitions) {
    public RequestToolSelection {
      tools = List.copyOf(tools);
      toolSummaries = List.copyOf(toolSummaries);
      toolDefinitions = List.copyOf(toolDefinitions);
    }

    public RequestToolSelection(List<Object> tools, List<ToolSummary> toolSummaries) {
      this(tools, toolSummaries, List.of());
    }
  }

  public record ToolSummary(
      String providerId,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      Map<String, String> scope) {
    public ToolSummary {
      policyTags = List.copyOf(policyTags);
      scope = Map.copyOf(scope);
    }

    static ToolSummary from(ToolDiscovery.ToolMatch match) {
      return new ToolSummary(
          match.providerId(),
          match.toolName(),
          match.description(),
          match.sideEffecting(),
          match.policyTags(),
          match.scope());
    }
  }

  public record ToolDefinitionSummary(
      String providerId,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      Map<String, String> scope,
      Map<String, Object> inputSchema,
      boolean available) {
    public ToolDefinitionSummary {
      policyTags = List.copyOf(policyTags);
      scope = Map.copyOf(scope);
      inputSchema = Map.copyOf(inputSchema);
    }

    static ToolDefinitionSummary from(ToolDiscovery.ToolDefinition definition) {
      return new ToolDefinitionSummary(
          definition.providerId(),
          definition.toolName(),
          definition.description(),
          definition.sideEffecting(),
          definition.policyTags(),
          definition.scope(),
          new LinkedHashMap<>(definition.inputSchema()),
          true);
    }

    static ToolDefinitionSummary unavailable(ToolDiscovery.ToolMatch match) {
      return new ToolDefinitionSummary(
          match.providerId(),
          match.toolName(),
          match.description(),
          match.sideEffecting(),
          match.policyTags(),
          match.scope(),
          Map.of("status", "unavailable"),
          false);
    }
  }
}
