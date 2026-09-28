package org.zalava.agent.domain;

import java.util.List;
import java.util.Map;

public record AgentToolSelection(
    List<Object> tools,
    List<ToolSummary> toolSummaries,
    List<ToolDefinitionSummary> toolDefinitions) {
  public AgentToolSelection {
    tools = List.copyOf(tools);
    toolSummaries = List.copyOf(toolSummaries);
    toolDefinitions = List.copyOf(toolDefinitions);
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
  }
}
