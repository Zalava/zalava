package org.zalava.capabilities.discovery.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;

public final class DefaultInstalledToolDiscovery implements ToolDiscovery {

  public static final int MAX_RESULTS = 20;

  private final ProviderCatalog providerCatalog;

  public DefaultInstalledToolDiscovery(ProviderCatalog providerCatalog) {
    this.providerCatalog = Objects.requireNonNull(providerCatalog, "providerCatalog");
  }

  @Override
  public List<ToolMatch> search(String query, int maxResults) {
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      throw new IllegalArgumentException("Zalava tool search query must not be blank");
    }
    if (maxResults < 1 || maxResults > MAX_RESULTS) {
      throw new IllegalArgumentException(
          "Zalava tool search maxResults must be between 1 and " + MAX_RESULTS);
    }

    List<String> queryTerms = Arrays.stream(normalizedQuery.split(" ")).distinct().toList();
    List<ScoredMatch> matches = new ArrayList<>();
    for (ZalavaProvider provider : providerCatalog.providers()) {
      if (!searchable(provider)) {
        continue;
      }
      List<ZalavaToolDescriptor> tools = provider.listTools();
      for (ZalavaToolDescriptor tool : tools) {
        int score = score(provider.descriptor(), tool, normalizedQuery, queryTerms);
        if (score > 0) {
          matches.add(new ScoredMatch(score, match(provider.descriptor(), tool)));
        }
      }
    }

    return matches.stream()
        .sorted(
            Comparator.comparingInt(ScoredMatch::score)
                .reversed()
                .thenComparing(match -> match.value().providerId())
                .thenComparing(match -> match.value().toolName()))
        .limit(maxResults)
        .map(ScoredMatch::value)
        .toList();
  }

  @Override
  public ToolDefinition load(String providerId, String toolName) {
    ZalavaProvider provider =
        providerCatalog
            .findProvider(providerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Zalava provider not found: " + providerId));
    if (!searchable(provider)) {
      throw new IllegalArgumentException(
          "Zalava provider is not available for tool discovery: " + providerId);
    }
    ZalavaToolDescriptor tool =
        provider.listTools().stream()
            .filter(candidate -> candidate.name().equals(toolName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Zalava tool not found: " + providerId + ":" + toolName));
    ProviderDescriptor descriptor = provider.descriptor();
    return new ToolDefinition(
        descriptor.providerId(),
        descriptor.displayName(),
        tool.name(),
        tool.description(),
        tool.sideEffecting(),
        tool.policyTags(),
        descriptor.policyTags(),
        descriptor.scope(),
        tool.inputSchema());
  }

  private static boolean searchable(ZalavaProvider provider) {
    return provider.capabilities().supportsTools();
  }

  private static int score(
      ProviderDescriptor provider,
      ZalavaToolDescriptor tool,
      String query,
      List<String> queryTerms) {
    String toolName = normalize(tool.name());
    String toolDescription = normalize(tool.description());
    String toolPolicyTags = normalize(String.join(" ", tool.policyTags()));
    String providerId = normalize(provider.providerId());
    String providerName = normalize(provider.displayName());
    String providerDescription = normalize(provider.description());
    String providerPolicyTags = normalize(String.join(" ", provider.policyTags()));
    String providerScope =
        normalize(
            provider.scope().entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .sorted()
                .reduce("", (left, right) -> left + " " + right));

    int score = 0;
    if (toolName.equals(query)) {
      score += 1_000;
    } else if (containsPhrase(toolName, query)) {
      score += 400;
    }
    score += termScore(queryTerms, toolName, 120);
    score += termScore(queryTerms, toolPolicyTags, 80);
    score += termScore(queryTerms, toolDescription, 50);
    score += termScore(queryTerms, providerName, 30);
    score += termScore(queryTerms, providerId, 25);
    score += termScore(queryTerms, providerPolicyTags, 20);
    score += termScore(queryTerms, providerScope, 15);
    score += termScore(queryTerms, providerDescription, 10);
    return score;
  }

  private static int termScore(List<String> terms, String candidate, int weight) {
    int score = 0;
    for (String term : terms) {
      if (!term.isBlank() && containsPhrase(candidate, term)) {
        score += weight;
      }
    }
    return score;
  }

  private static boolean containsPhrase(String candidate, String phrase) {
    return (" " + candidate + " ").contains(" " + phrase + " ");
  }

  private static ToolMatch match(ProviderDescriptor provider, ZalavaToolDescriptor tool) {
    return new ToolMatch(
        provider.providerId(),
        provider.displayName(),
        tool.name(),
        tool.description(),
        tool.sideEffecting(),
        tool.policyTags(),
        provider.policyTags(),
        provider.scope());
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceAll(" +", " ");
  }

  private record ScoredMatch(int score, ToolMatch value) {}
}
