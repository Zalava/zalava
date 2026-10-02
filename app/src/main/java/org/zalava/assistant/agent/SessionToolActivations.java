package org.zalava.assistant.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;

final class SessionToolActivations {

  private final int maxTools;
  private final Map<String, LinkedHashMap<ToolKey, ToolDiscovery.ToolMatch>> activations =
      new ConcurrentHashMap<>();

  SessionToolActivations(int maxTools) {
    this.maxTools = maxTools;
  }

  List<ToolDiscovery.ToolMatch> activatedTools(String conversationId) {
    LinkedHashMap<ToolKey, ToolDiscovery.ToolMatch> tools = activations.get(conversationId);
    if (tools == null) {
      return List.of();
    }
    synchronized (tools) {
      return List.copyOf(tools.values());
    }
  }

  void activate(String conversationId, List<ToolDiscovery.ToolMatch> matches) {
    if (matches.isEmpty()) {
      return;
    }
    LinkedHashMap<ToolKey, ToolDiscovery.ToolMatch> tools =
        activations.computeIfAbsent(conversationId, ignored -> new LinkedHashMap<>());
    synchronized (tools) {
      for (ToolDiscovery.ToolMatch match : matches) {
        if (tools.size() >= maxTools && !tools.containsKey(ToolKey.from(match))) {
          continue;
        }
        tools.putIfAbsent(ToolKey.from(match), match);
      }
    }
  }

  private record ToolKey(String providerId, String toolName) {
    static ToolKey from(ToolDiscovery.ToolMatch match) {
      return new ToolKey(match.providerId(), match.toolName());
    }
  }
}
