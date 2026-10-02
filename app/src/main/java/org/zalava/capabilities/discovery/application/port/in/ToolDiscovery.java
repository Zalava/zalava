package org.zalava.capabilities.discovery.application.port.in;

import java.util.List;
import java.util.Map;
import org.zalava.ZalavaToolInputSchemas;

public interface ToolDiscovery {

  List<ToolMatch> search(String query, int maxResults);

  ToolDefinition load(String providerId, String toolName);

  record ToolMatch(
      String providerId,
      String providerDisplayName,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      List<String> providerPolicyTags,
      Map<String, String> scope) {
    public ToolMatch {
      policyTags = List.copyOf(policyTags);
      providerPolicyTags = List.copyOf(providerPolicyTags);
      scope = Map.copyOf(scope);
    }
  }

  record ToolDefinition(
      String providerId,
      String providerDisplayName,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      List<String> providerPolicyTags,
      Map<String, String> scope,
      Map<String, Object> inputSchema) {
    public ToolDefinition {
      policyTags = List.copyOf(policyTags);
      providerPolicyTags = List.copyOf(providerPolicyTags);
      scope = Map.copyOf(scope);
      inputSchema = ZalavaToolInputSchemas.immutable(inputSchema);
    }
  }
}
