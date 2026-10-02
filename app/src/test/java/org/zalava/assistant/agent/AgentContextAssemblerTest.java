package org.zalava.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.knowledge.memory.AgentMemory;
import org.zalava.knowledge.memory.AgentMemoryDraft;
import org.zalava.knowledge.memory.AgentMemoryScope;
import org.zalava.knowledge.memory.AgentMemoryStore;

@SuppressWarnings("deprecation")
class AgentContextAssemblerTest {

  @Test
  void assemblesUserPromptWithBudgetMetrics() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(8);

    AgentContextAssembly context = assembler.assemble("build the module");

    assertThat(context.prompt()).isEqualTo("build th");
    assertThat(context.sourceCount()).isEqualTo(1);
    assertThat(context.characterBudget()).isEqualTo(8);
    assertThat(context.charactersUsed()).isEqualTo(8);
    assertThat(context.sourceMetrics())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.sourceType()).isEqualTo("user_prompt");
              assertThat(source.charactersAvailable()).isEqualTo(16);
              assertThat(source.charactersUsed()).isEqualTo(8);
            });
  }

  @Test
  void preservesPromptInsideBudget() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(20);

    AgentContextAssembly context = assembler.assemble("short prompt");

    assertThat(context.prompt()).isEqualTo("short prompt");
    assertThat(context.charactersUsed()).isEqualTo(12);
    assertThat(context.sourceMetrics())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.charactersAvailable()).isEqualTo(12);
              assertThat(source.charactersUsed()).isEqualTo(12);
            });
  }

  @Test
  void addsSelectedToolSummariesAsSecondContextSource() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(300);

    AgentContextAssembly context =
        assembler.assemble(
            "update list",
            new AgentRequestTools.RequestToolSelection(
                List.of(), List.of(toolSummary()), List.of()));

    assertThat(context.prompt())
        .contains("update list")
        .contains("Selected SEA tool summaries:")
        .contains("shopping-list/addItem")
        .contains("sideEffects=true");
    assertThat(context.sourceCount()).isEqualTo(2);
    assertThat(context.sourceMetrics())
        .extracting(AgentContextAssembly.SourceMetric::sourceType)
        .containsExactly("user_prompt", "selected_tool_summaries");
  }

  @Test
  void addsSelectedToolDefinitionsAfterSummaries() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(500);

    AgentContextAssembly context =
        assembler.assemble(
            "update list",
            new AgentRequestTools.RequestToolSelection(
                List.of(), List.of(toolSummary()), List.of(toolDefinition())));

    assertThat(context.prompt())
        .contains("Selected SEA tool summaries:")
        .contains("Selected SEA tool definitions:")
        .contains("inputSchema=")
        .contains("type=object");
    assertThat(context.sourceMetrics())
        .extracting(AgentContextAssembly.SourceMetric::sourceType)
        .containsExactly("user_prompt", "selected_tool_summaries", "selected_tool_definitions");
  }

  @Test
  void keepsAssembledPromptWithinBudgetWhenToolSummariesAreLong() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(80);

    AgentContextAssembly context =
        assembler.assemble(
            "short",
            List.of(
                new AgentRequestTools.ToolSummary(
                    "provider",
                    "tool",
                    "x".repeat(200),
                    false,
                    List.of("sea_backed"),
                    java.util.Map.of())));

    assertThat(context.prompt()).hasSizeLessThanOrEqualTo(80);
    assertThat(context.charactersUsed()).isEqualTo(context.prompt().length());
    assertThat(context.sourceMetrics())
        .last()
        .satisfies(
            source -> assertThat(source.charactersUsed()).isLessThan(source.charactersAvailable()));
  }

  @Test
  void keepsAssembledPromptWithinBudgetWhenToolDefinitionsAreLong() {
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(180);

    AgentContextAssembly context =
        assembler.assemble(
            "short",
            new AgentRequestTools.RequestToolSelection(
                List.of(),
                List.of(toolSummary()),
                List.of(
                    new AgentRequestTools.ToolDefinitionSummary(
                        "provider",
                        "tool",
                        "x".repeat(300),
                        false,
                        List.of("sea_backed"),
                        java.util.Map.of(),
                        java.util.Map.of("type", "object", "description", "y".repeat(300)),
                        true))));

    assertThat(context.prompt()).hasSizeLessThanOrEqualTo(180);
    assertThat(context.charactersUsed()).isEqualTo(context.prompt().length());
    assertThat(context.sourceMetrics())
        .last()
        .satisfies(
            source -> assertThat(source.charactersUsed()).isLessThan(source.charactersAvailable()));
  }

  @Test
  void addsSelectedMemoriesAfterToolContext() {
    TestMemoryStore memoryStore =
        new TestMemoryStore(
            List.of(
                new AgentMemory(
                    "memory-1",
                    AgentMemoryScope.PROJECT,
                    "SEA uses draft pull requests for every scoped step.",
                    Map.of("topic", "workflow"),
                    Instant.parse("2026-06-28T10:00:00Z"))));
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(800, 3, memoryStore);

    AgentContextAssembly context =
        assembler.assemble(
            "draft pull request workflow",
            new AgentRequestTools.RequestToolSelection(
                List.of(), List.of(toolSummary()), List.of(toolDefinition())));

    assertThat(memoryStore.queries).containsExactly("draft pull request workflow");
    assertThat(context.prompt())
        .contains("Selected memories:")
        .contains("PROJECT memory-1")
        .contains("draft pull requests");
    assertThat(context.sourceMetrics())
        .extracting(AgentContextAssembly.SourceMetric::sourceType)
        .containsExactly(
            "user_prompt",
            "selected_tool_summaries",
            "selected_tool_definitions",
            "selected_memories");
  }

  @Test
  void keepsAssembledPromptWithinBudgetWhenSelectedMemoriesAreLong() {
    TestMemoryStore memoryStore =
        new TestMemoryStore(
            List.of(
                new AgentMemory(
                    "memory-1",
                    AgentMemoryScope.PROJECT,
                    "m".repeat(300),
                    Map.of(),
                    Instant.parse("2026-06-28T10:00:00Z"))));
    DefaultAgentContextAssembler assembler = new DefaultAgentContextAssembler(120, 3, memoryStore);

    AgentContextAssembly context = assembler.assemble("memory query");

    assertThat(context.prompt()).hasSizeLessThanOrEqualTo(120);
    assertThat(context.charactersUsed()).isEqualTo(context.prompt().length());
    assertThat(context.sourceMetrics())
        .last()
        .satisfies(
            source -> assertThat(source.charactersUsed()).isLessThan(source.charactersAvailable()));
  }

  private static AgentRequestTools.ToolSummary toolSummary() {
    return new AgentRequestTools.ToolSummary(
        "shopping-list",
        "addItem",
        "Add an item to the active list",
        true,
        List.of("sea_backed", "shopping-list"),
        java.util.Map.of("list", "active"));
  }

  private static AgentRequestTools.ToolDefinitionSummary toolDefinition() {
    return new AgentRequestTools.ToolDefinitionSummary(
        "shopping-list",
        "addItem",
        "Add an item to the active list",
        true,
        List.of("sea_backed", "shopping-list"),
        java.util.Map.of("list", "active"),
        java.util.Map.of(
            "type",
            "object",
            "properties",
            java.util.Map.of("item", java.util.Map.of("type", "string"))),
        true);
  }

  private static final class TestMemoryStore implements AgentMemoryStore {

    private final List<AgentMemory> memories;
    private final List<String> queries = new ArrayList<>();

    private TestMemoryStore(List<AgentMemory> memories) {
      this.memories = memories;
    }

    @Override
    public AgentMemory remember(AgentMemoryDraft draft) {
      throw new UnsupportedOperationException("remember is not used by this test");
    }

    @Override
    public List<AgentMemory> recent(int limit) {
      throw new UnsupportedOperationException("recent is not used by this test");
    }

    @Override
    public List<AgentMemory> search(String query, int limit) {
      queries.add(query);
      return memories.stream().limit(limit).toList();
    }
  }
}
