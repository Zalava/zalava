package org.zalava.assistant.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackNames;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolReferences;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.identity.accounts.domain.AccountRole;

/**
 * Optional policy-filtered Tool Search over an already bounded candidate set.
 *
 * <p>It reuses Zalava's session-scoped {@link ToolIndex} boundary (which still owns the maintained
 * 1–10 result limit) and re-applies {@link DynamicToolActivationPolicy} to every searched match, so
 * it can only narrow an authorized selection. When the index is disabled, the query/candidates are
 * empty, or the search fails, it returns no matches and the caller keeps its deterministic
 * preselection; that fallback equivalence is a required CTX-02 property.
 */
public final class PolicyFilteredToolSearch {

  private static final int INDEX_RESULT_LIMIT = 10;

  private final ToolIndex toolIndex;
  private final DynamicToolActivationPolicy activationPolicy;
  private final boolean enabled;

  public PolicyFilteredToolSearch(ToolIndex toolIndex) {
    this(toolIndex, new DynamicToolActivationPolicy(), true);
  }

  public PolicyFilteredToolSearch(ToolIndex toolIndex, boolean enabled) {
    this(toolIndex, new DynamicToolActivationPolicy(), enabled);
  }

  public PolicyFilteredToolSearch(
      ToolIndex toolIndex, DynamicToolActivationPolicy activationPolicy, boolean enabled) {
    this.toolIndex = toolIndex;
    this.activationPolicy = Objects.requireNonNull(activationPolicy, "activationPolicy");
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled && toolIndex != null;
  }

  public List<ToolDiscovery.ToolMatch> select(
      String sessionId,
      String query,
      List<ToolDiscovery.ToolMatch> candidates,
      AccountRole role,
      int maxResults) {
    if (!enabled() || candidates == null || candidates.isEmpty() || maxResults < 1) {
      return List.of();
    }
    if (sessionId == null || sessionId.isBlank() || query == null || query.isBlank()) {
      return List.of();
    }
    Map<String, ToolDiscovery.ToolMatch> byCallbackName = new LinkedHashMap<>();
    for (ToolDiscovery.ToolMatch candidate : candidates) {
      if (candidate != null) {
        byCallbackName.put(
            ZalavaToolCallbackNames.forTool(candidate.providerId(), candidate.toolName()),
            candidate);
      }
    }
    try {
      toolIndex.indexTools(sessionId, candidates.stream().map(ZalavaToolReferences::from).toList());
      ToolSearchResponse response =
          toolIndex.search(
              new ToolSearchRequest(
                  sessionId, query, Math.min(maxResults, INDEX_RESULT_LIMIT), null));
      if (response == null || response.toolReferences() == null) {
        return List.of();
      }
      return response.toolReferences().stream()
          .map(reference -> byCallbackName.get(reference.toolName()))
          .filter(Objects::nonNull)
          .filter(match -> activationPolicy.allows(match, role))
          .distinct()
          .limit(maxResults)
          .toList();
    } catch (RuntimeException ignored) {
      return List.of();
    } finally {
      toolIndex.clearIndex(sessionId);
    }
  }
}
