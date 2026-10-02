package org.zalava.capabilities.operation.adapter.in.agent;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.assistant.agent.DynamicToolActivationPolicy;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.tasks.domain.TaskExecutionContext;

public final class SeaProviderTool {

  private static final int DEFAULT_SEARCH_RESULTS = 5;

  private final SeaProviderToolInvoker invoker;
  private final ToolDiscovery discovery;
  private final ActorExecutionContext actorExecution;
  private final DynamicToolActivationPolicy activationPolicy = new DynamicToolActivationPolicy();

  public SeaProviderTool(
      ProviderToolOperations operations,
      ToolDiscovery discovery,
      TaskExecutionContext taskExecutionContext) {
    this(operations, discovery, taskExecutionContext, null);
  }

  public SeaProviderTool(
      ProviderToolOperations operations,
      ToolDiscovery discovery,
      TaskExecutionContext taskExecutionContext,
      ActorExecutionContext actorExecution) {
    this.invoker = new SeaProviderToolInvoker(operations, taskExecutionContext, actorExecution);
    this.discovery = discovery;
    this.actorExecution = actorExecution;
  }

  @Tool(
      description =
          """
            Search installed SEA-native provider tools before invoking one.
            Returns compact provider-scoped matches with provider ID, tool name,
            description, scope, policy tags, and side-effect classification.
            """)
  public List<ToolDiscovery.ToolMatch> searchSeaProviderTools(
      String query,
      @ToolParam(description = "Maximum number of matches to return", required = false)
          Integer maxResults) {
    var role =
        actorExecution == null
            ? null
            : actorExecution.currentPrincipal().map(value -> value.role()).orElse(null);
    List<ToolDiscovery.ToolMatch> matches =
        discovery.search(query, maxResults == null ? DEFAULT_SEARCH_RESULTS : maxResults);
    return role == AccountRole.MEMBER
        ? matches.stream().filter(match -> activationPolicy.allows(match, role)).toList()
        : matches;
  }

  @Tool(
      description =
          """
            Load the full definition and JSON input schema for one installed
            SEA-native provider tool selected from searchSeaProviderTools.
            """)
  public ToolDiscovery.ToolDefinition loadSeaProviderTool(String providerId, String toolName) {
    ToolDiscovery.ToolDefinition definition = discovery.load(providerId, toolName);
    var role =
        actorExecution == null
            ? null
            : actorExecution.currentPrincipal().map(value -> value.role()).orElse(null);
    var match =
        new ToolDiscovery.ToolMatch(
            definition.providerId(),
            definition.providerDisplayName(),
            definition.toolName(),
            definition.description(),
            definition.sideEffecting(),
            definition.policyTags(),
            definition.providerPolicyTags(),
            definition.scope());
    if (role == AccountRole.MEMBER && !activationPolicy.allows(match, role)) {
      throw new UnsupportedOperationException("Provider tool is unavailable to this account");
    }
    return definition;
  }

  @Tool(
      description =
          """
            Invoke a SEA-native provider tool by provider ID and tool name.
            Use searchSeaProviderTools first to find the provider ID and tool name.
            Pass tool arguments as a JSON object string. Side-effecting operations
            are routed through SEA's out-of-band approval workflow.
            """)
  public ZalavaOperationResult invokeSeaProviderTool(
      String providerId, String toolName, String argumentsJson) {
    return invoker.invoke(providerId, toolName, argumentsJson, "sea-provider-tool");
  }
}
