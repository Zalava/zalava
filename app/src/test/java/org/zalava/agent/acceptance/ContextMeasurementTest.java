package org.zalava.agent.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.agent.AgentRequestTools;
import org.zalava.agent.InMemoryAgentRunRecorder;
import org.zalava.agent.PolicyFilteredToolSearch;
import org.zalava.agent.application.ContextSourceBudgets;
import org.zalava.agent.application.DefaultAgentContextAssembler;
import org.zalava.agent.application.DefaultAgentExecution;
import org.zalava.agent.application.port.out.AgentModel;
import org.zalava.agent.application.port.out.AgentToolSelector;
import org.zalava.agent.domain.AgentContext;
import org.zalava.agent.domain.AgentToolSelection;
import org.zalava.discovery.adapter.out.springai.SeaToolCallbackCatalog;
import org.zalava.discovery.adapter.out.springai.SeaToolIndex;
import org.zalava.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.discovery.application.port.in.ToolDiscovery;
import org.zalava.memory.application.port.in.MemoryQueries;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * Opt-in CTX-02 progressive-context measurement lane.
 *
 * <p>Runs one synthetic tool corpus through the real deterministic preselection path and the new
 * policy-filtered Tool Search path, driven by a scripted {@link AgentModel}, and prints {@code
 * CTX-METRICS} lines that the CTX-02 plan records verbatim. It is excluded from {@code :app:check}
 * and runs through {@code contextMeasurementTest}.
 */
@Tag("context-measurement")
class ContextMeasurementTest {

  private static final String QUERY = "add an item to my shopping list";
  private static final String UNMATCHED_QUERY = "zzz unmatched phrase zzz";
  private static final Set<String> EXPECTED_RELEVANT = Set.of("addItem", "listItems");

  @Test
  void compareDeterministicPreselectionAndPolicyFilteredToolSearchOnOneCorpus() {
    List<ToolDiscovery.ToolMatch> corpus = corpus();
    SeaToolCallbackCatalog callbackCatalog = callbackCatalog();
    CorpusDiscovery discovery = new CorpusDiscovery(corpus);

    Result deterministic =
        measure(
            "deterministic",
            build(discovery, callbackCatalog, false),
            "ctx-deterministic",
            QUERY,
            new DefaultAgentContextAssembler(20_000, 3, memoryQueries()));
    Result search =
        measure(
            "policy-filtered-search",
            build(discovery, callbackCatalog, true),
            "ctx-search",
            QUERY,
            new DefaultAgentContextAssembler(20_000, 3, memoryQueries()));

    printMetrics(deterministic);
    printMetrics(search);

    assertThat(deterministic.selectedToolNames()).contains("addItem", "listItems");
    assertThat(search.selectedToolNames()).containsAll(EXPECTED_RELEVANT);
    assertThat(search.selectedToolNames())
        .doesNotContain("current_time", "current_weather", "readText", "ping");
    assertThat(search.selectedToolNames().size())
        .isLessThan(deterministic.selectedToolNames().size());
    assertThat(search.definitionSourceUsed()).isLessThan(deterministic.definitionSourceUsed());

    int deterministicAbsent = absence(deterministic, corpus);
    int searchAbsent = absence(search, corpus);
    assertThat(deterministicAbsent)
        .isEqualTo(corpus.size() - deterministic.selectedToolNames().size());
    assertThat(searchAbsent).isEqualTo(corpus.size() - search.selectedToolNames().size());
    System.out.printf(
        "CTX-METRICS absence non-selected-definitions=%d absent=%d%n",
        corpus.size() - search.selectedToolNames().size(), searchAbsent);

    Result fallback =
        measure(
            "search-fallback",
            build(discovery, callbackCatalog, true),
            "ctx-search-fallback",
            UNMATCHED_QUERY,
            new DefaultAgentContextAssembler(20_000, 3, memoryQueries()));
    Result disabled =
        measure(
            "search-disabled",
            build(discovery, callbackCatalog, false),
            "ctx-deterministic-disabled",
            UNMATCHED_QUERY,
            new DefaultAgentContextAssembler(20_000, 3, memoryQueries()));
    assertThat(fallback.selectedToolNames()).isEqualTo(disabled.selectedToolNames());
    System.out.printf(
        "CTX-METRICS fallback equivalent=true deterministic=%s fallback=%s%n",
        disabled.selectedToolNames(), fallback.selectedToolNames());

    Result budgeted =
        measure(
            "search-budgeted",
            build(discovery, callbackCatalog, true),
            "ctx-search-budgeted",
            QUERY,
            new DefaultAgentContextAssembler(
                20_000, 3, memoryQueries(), null, new ContextSourceBudgets(80, 150, 40, 0)));
    printMetrics(budgeted);
    assertThat(budgeted.summarySourceUsed()).isLessThanOrEqualTo(80);
    assertThat(budgeted.definitionSourceUsed()).isLessThanOrEqualTo(150);
    assertThat(budgeted.memorySourceUsed()).isLessThanOrEqualTo(40);
    assertThat(budgeted.totalUsed()).isLessThanOrEqualTo(20_000);
  }

  private static Result measure(
      String strategy,
      AgentRequestTools requestTools,
      String conversationId,
      String query,
      DefaultAgentContextAssembler assembler) {
    Recorder recorder = new Recorder(requestTools);
    DefaultAgentExecution execution =
        new DefaultAgentExecution(
            recorder,
            recorder.selector(),
            assembler,
            new InMemoryAgentRunRecorder(),
            () -> Instant.EPOCH,
            () -> strategy);
    execution.respondTo(conversationId, query);
    AgentToolSelection selection = recorder.selection();
    AgentContext context = assembler.assemble(query, selection);
    assertThat(recorder.prompt()).isEqualTo(context.prompt());
    Set<String> names = new LinkedHashSet<>();
    selection.toolDefinitions().forEach(definition -> names.add(definition.toolName()));
    return new Result(strategy, context, names, 11);
  }

  private static AgentRequestTools build(
      CorpusDiscovery discovery, SeaToolCallbackCatalog callbackCatalog, boolean searchEnabled) {
    if (!searchEnabled) {
      return new AgentRequestTools(List.of(), discovery, callbackCatalog);
    }
    return new AgentRequestTools(
        List.of(),
        discovery,
        callbackCatalog,
        List.of(),
        RemoteCapabilityDiscovery.noop(),
        new PolicyFilteredToolSearch(new SeaToolIndex()));
  }

  private static int absence(Result result, List<ToolDiscovery.ToolMatch> corpus) {
    int absent = 0;
    for (ToolDiscovery.ToolMatch match : corpus) {
      if (!result.selectedToolNames().contains(match.toolName())
          && !result.context().prompt().contains(schemaMarker(match.toolName()))) {
        absent++;
      }
    }
    return absent;
  }

  private static void printMetrics(Result result) {
    int relevant = 0;
    for (String name : result.selectedToolNames()) {
      if (EXPECTED_RELEVANT.contains(name)) {
        relevant++;
      }
    }
    System.out.printf(
        java.util.Locale.ROOT,
        "CTX-METRICS strategy=%s candidates=%d selected=%d relevant=%d precision=%.3f definitions=%d "
            + "summary-chars=%d definition-chars=%d memory-chars=%d total-chars=%d tools=%s%n",
        result.strategy(),
        result.candidateCount(),
        result.selectedToolNames().size(),
        relevant,
        result.selectedToolNames().isEmpty()
            ? 0.0
            : (double) relevant / result.selectedToolNames().size(),
        result.selectedToolNames().size(),
        result.summarySourceUsed(),
        result.definitionSourceUsed(),
        result.memorySourceUsed(),
        result.totalUsed(),
        result.selectedToolNames().stream().sorted().toList());
  }

  private static List<ToolDiscovery.ToolMatch> corpus() {
    return List.of(
        match(
            "shopping-list",
            "addItem",
            "Add an item to the active shopping list.",
            false,
            List.of()),
        match(
            "shopping-list",
            "listItems",
            "List items in the active shopping list.",
            false,
            List.of()),
        match("time-provider", "current_time", "Return the current time.", false, List.of()),
        match(
            "weather-provider",
            "current_weather",
            "Return current weather for a location.",
            false,
            List.of()),
        match("filesystem", "readText", "Read text from the workspace.", false, List.of()),
        match("filesystem", "writeText", "Write text to the workspace.", true, List.of()),
        match("brave-search", "web_search", "Search the public web.", false, List.of()),
        match("docker", "list_containers", "List running containers.", false, List.of()),
        match("mcp-bridge", "call", "Call a remote MCP tool.", false, List.of()),
        match(
            "host-shell",
            "executeAdmin",
            "Add an item to the shell queue.",
            true,
            List.of("shell")),
        match("telemetry", "ping", "Send a heartbeat.", false, List.of()));
  }

  private static ToolDiscovery.ToolMatch match(
      String providerId,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> tags) {
    List<String> policyTags =
        java.util.stream.Stream.concat(java.util.stream.Stream.of("sea_backed"), tags.stream())
            .distinct()
            .toList();
    return new ToolDiscovery.ToolMatch(
        providerId,
        providerId,
        toolName,
        description,
        sideEffecting,
        policyTags,
        List.of("sea_backed"),
        Map.of("root", "workspace"));
  }

  private static SeaToolCallbackCatalog callbackCatalog() {
    SeaToolCallbackCatalog catalog = mock(SeaToolCallbackCatalog.class);
    when(catalog.entries()).thenReturn(List.of());
    when(catalog.callback(anyString(), anyString()))
        .thenAnswer(invocation -> mock(ToolCallback.class));
    return catalog;
  }

  private static MemoryQueries memoryQueries() {
    return new MemoryQueries() {
      @Override
      public List<Memory> recent(int limit) {
        throw new UnsupportedOperationException("recent is not used by this test");
      }

      @Override
      public List<Memory> search(String query, int limit) {
        return List.of(
            new Memory(
                "memory-1",
                MemoryScope.PROJECT,
                "Shopping list items are household-shared.",
                Map.of("topic", "shopping"),
                Instant.EPOCH));
      }
    };
  }

  private static String schemaMarker(String toolName) {
    return "schema-marker-" + toolName;
  }

  private static Map<String, Object> schema(String toolName) {
    return Map.of("type", "object", "marker", schemaMarker(toolName));
  }

  private static AgentToolSelection toSelection(AgentRequestTools.RequestToolSelection selection) {
    return new AgentToolSelection(
        selection.tools(),
        selection.toolSummaries().stream()
            .map(
                summary ->
                    new AgentToolSelection.ToolSummary(
                        summary.providerId(),
                        summary.toolName(),
                        summary.description(),
                        summary.sideEffecting(),
                        summary.policyTags(),
                        summary.scope()))
            .toList(),
        selection.toolDefinitions().stream()
            .map(
                definition ->
                    new AgentToolSelection.ToolDefinitionSummary(
                        definition.providerId(),
                        definition.toolName(),
                        definition.description(),
                        definition.sideEffecting(),
                        definition.policyTags(),
                        definition.scope(),
                        definition.inputSchema(),
                        definition.available()))
            .toList());
  }

  private record Result(
      String strategy, AgentContext context, Set<String> selectedToolNames, int candidateCount) {

    int summarySourceUsed() {
      return sourceUsed("selected_tool_summaries");
    }

    int definitionSourceUsed() {
      return sourceUsed("selected_tool_definitions");
    }

    int memorySourceUsed() {
      return sourceUsed("selected_memories");
    }

    int totalUsed() {
      return context.charactersUsed();
    }

    private int sourceUsed(String sourceType) {
      return context.sourceMetrics().stream()
          .filter(metric -> metric.sourceType().equals(sourceType))
          .mapToInt(AgentContext.SourceMetric::charactersUsed)
          .findFirst()
          .orElse(0);
    }
  }

  private static final class Recorder implements AgentModel {
    private final AgentRequestTools requestTools;
    private final AtomicReference<AgentToolSelection> selection = new AtomicReference<>();
    private final AtomicReference<String> prompt = new AtomicReference<>();

    private Recorder(AgentRequestTools requestTools) {
      this.requestTools = requestTools;
    }

    AgentToolSelector selector() {
      return (conversationId, input) -> {
        AgentToolSelection resolved =
            toSelection(requestTools.resolve(conversationId, input, AccountRole.ADMIN));
        selection.set(resolved);
        return resolved;
      };
    }

    AgentToolSelection selection() {
      return selection.get();
    }

    String prompt() {
      return prompt.get();
    }

    @Override
    public String conversational(String conversationId, String value, List<Object> tools) {
      prompt.set(value);
      return "scripted-ok";
    }

    @Override
    public <T> T structured(
        String conversationId, String value, List<Object> tools, Class<T> resultType) {
      throw new UnsupportedOperationException("structured is not used by this test");
    }
  }

  private static final class CorpusDiscovery implements ToolDiscovery {
    private final List<ToolMatch> corpus;

    private CorpusDiscovery(List<ToolMatch> corpus) {
      this.corpus = corpus;
    }

    @Override
    public List<ToolMatch> search(String query, int maxResults) {
      return corpus.stream().limit(maxResults).toList();
    }

    @Override
    public ToolDefinition load(String providerId, String toolName) {
      ToolMatch match =
          corpus.stream()
              .filter(candidate -> candidate.providerId().equals(providerId))
              .filter(candidate -> candidate.toolName().equals(toolName))
              .findFirst()
              .orElseThrow();
      return new ToolDefinition(
          match.providerId(),
          match.providerDisplayName(),
          match.toolName(),
          match.description(),
          match.sideEffecting(),
          match.policyTags(),
          match.providerPolicyTags(),
          match.scope(),
          schema(toolName));
    }
  }
}
