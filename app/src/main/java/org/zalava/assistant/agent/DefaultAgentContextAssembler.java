package org.zalava.assistant.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zalava.knowledge.memory.AgentMemoryStore;
import org.zalava.knowledge.memory.application.port.in.MemoryQueries;
import org.zalava.knowledge.memory.domain.Memory;

@Component
public final class DefaultAgentContextAssembler implements AgentContextAssembler {

  static final int DEFAULT_CHARACTER_BUDGET = 64_000;
  static final int DEFAULT_MEMORY_LIMIT = 3;

  private final int characterBudget;
  private final int memoryLimit;
  private final MemoryQueries memoryQueries;

  @Autowired
  public DefaultAgentContextAssembler(
      @Value("${agent.context.character-budget:" + DEFAULT_CHARACTER_BUDGET + "}")
          int characterBudget,
      @Value("${agent.context.memory-limit:" + DEFAULT_MEMORY_LIMIT + "}") int memoryLimit,
      MemoryQueries memoryQueries) {
    if (characterBudget < 0) {
      throw new IllegalArgumentException("characterBudget must not be negative");
    }
    if (memoryLimit < 0) {
      throw new IllegalArgumentException("memoryLimit must not be negative");
    }
    this.characterBudget = characterBudget;
    this.memoryLimit = memoryLimit;
    this.memoryQueries = memoryQueries;
  }

  DefaultAgentContextAssembler(int characterBudget) {
    this(characterBudget, DEFAULT_MEMORY_LIMIT, (MemoryQueries) null);
  }

  @Deprecated(forRemoval = false)
  DefaultAgentContextAssembler(
      int characterBudget, int memoryLimit, AgentMemoryStore legacyMemoryStore) {
    this(
        characterBudget,
        memoryLimit,
        legacyMemoryStore == null
            ? null
            : new MemoryQueries() {
              @Override
              public List<Memory> recent(int limit) {
                return legacyMemoryStore.recent(limit).stream()
                    .map(
                        memory ->
                            new Memory(
                                memory.id(),
                                org.zalava.knowledge.memory.domain.MemoryScope.valueOf(
                                    memory.scope().name()),
                                memory.text(),
                                memory.metadata(),
                                memory.createdAt()))
                    .toList();
              }

              @Override
              public List<Memory> search(String query, int limit) {
                return legacyMemoryStore.search(query, limit).stream()
                    .map(
                        memory ->
                            new Memory(
                                memory.id(),
                                org.zalava.knowledge.memory.domain.MemoryScope.valueOf(
                                    memory.scope().name()),
                                memory.text(),
                                memory.metadata(),
                                memory.createdAt()))
                    .toList();
              }
            });
  }

  @Override
  public AgentContextAssembly assemble(String input) {
    return assemble(input, List.of());
  }

  @Override
  public AgentContextAssembly assemble(
      String input, List<AgentRequestTools.ToolSummary> toolSummaries) {
    return assemble(
        input, new AgentRequestTools.RequestToolSelection(List.of(), toolSummaries, List.of()));
  }

  @Override
  public AgentContextAssembly assemble(
      String input, AgentRequestTools.RequestToolSelection toolSelection) {
    String prompt = input == null ? "" : input;
    int userPromptBudget = characterBudget;
    String boundedPrompt =
        prompt.length() <= userPromptBudget ? prompt : prompt.substring(0, userPromptBudget);
    List<AgentRequestTools.ToolSummary> toolSummaries =
        toolSelection == null ? List.of() : toolSelection.toolSummaries();
    List<AgentRequestTools.ToolDefinitionSummary> toolDefinitions =
        toolSelection == null ? List.of() : toolSelection.toolDefinitions();
    String toolSummaryText = toolSummaryText(toolSummaries);
    String boundedToolSummary = boundedToolSummary(boundedPrompt, toolSummaryText);
    String promptWithSummaries =
        promptWithSection(boundedPrompt, "Selected SEA tool summaries:", boundedToolSummary);
    String definitionText = toolDefinitionText(toolDefinitions);
    String boundedDefinitions =
        boundedSection(promptWithSummaries, "Selected SEA tool definitions:", definitionText);
    String promptWithDefinitions =
        promptWithSection(
            promptWithSummaries, "Selected SEA tool definitions:", boundedDefinitions);
    String memoryText = memoryText(selectMemories(prompt));
    String boundedMemories =
        boundedSection(promptWithDefinitions, "Selected memories:", memoryText);
    String assembledPrompt =
        promptWithSection(promptWithDefinitions, "Selected memories:", boundedMemories);
    List<AgentContextAssembly.SourceMetric> sourceMetrics = new ArrayList<>();
    sourceMetrics.add(
        new AgentContextAssembly.SourceMetric(
            "user_prompt", prompt.length(), boundedPrompt.length()));
    if (!toolSummaries.isEmpty()) {
      sourceMetrics.add(
          new AgentContextAssembly.SourceMetric(
              "selected_tool_summaries", toolSummaryText.length(), boundedToolSummary.length()));
    }
    if (!toolDefinitions.isEmpty()) {
      sourceMetrics.add(
          new AgentContextAssembly.SourceMetric(
              "selected_tool_definitions", definitionText.length(), boundedDefinitions.length()));
    }
    if (!memoryText.isEmpty()) {
      sourceMetrics.add(
          new AgentContextAssembly.SourceMetric(
              "selected_memories", memoryText.length(), boundedMemories.length()));
    }
    return new AgentContextAssembly(
        assembledPrompt,
        sourceMetrics.size(),
        characterBudget,
        assembledPrompt.length(),
        sourceMetrics);
  }

  private static String promptWithSection(String prompt, String heading, String content) {
    if (content.isBlank()) {
      return prompt;
    }
    return prompt
        + System.lineSeparator()
        + System.lineSeparator()
        + heading
        + System.lineSeparator()
        + content;
  }

  private String boundedToolSummary(String prompt, String toolSummary) {
    return boundedSection(prompt, "Selected SEA tool summaries:", toolSummary);
  }

  private String boundedSection(String currentPrompt, String heading, String content) {
    if (content.isBlank()) {
      return "";
    }
    String prefix =
        System.lineSeparator() + System.lineSeparator() + heading + System.lineSeparator();
    int remainingBudget = characterBudget - currentPrompt.length() - prefix.length();
    if (remainingBudget <= 0) {
      return "";
    }
    return content.length() <= remainingBudget ? content : content.substring(0, remainingBudget);
  }

  private static String toolSummaryText(List<AgentRequestTools.ToolSummary> toolSummaries) {
    if (toolSummaries == null || toolSummaries.isEmpty()) {
      return "";
    }
    return toolSummaries.stream()
        .map(
            summary ->
                "- %s/%s: %s; sideEffects=%s; policyTags=%s; scope=%s"
                    .formatted(
                        summary.providerId(),
                        summary.toolName(),
                        summary.description(),
                        summary.sideEffecting(),
                        summary.policyTags(),
                        summary.scope()))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }

  private static String toolDefinitionText(
      List<AgentRequestTools.ToolDefinitionSummary> toolDefinitions) {
    if (toolDefinitions == null || toolDefinitions.isEmpty()) {
      return "";
    }
    return toolDefinitions.stream()
        .map(
            definition ->
                "- %s/%s: %s; available=%s; sideEffects=%s; policyTags=%s; scope=%s; inputSchema=%s"
                    .formatted(
                        definition.providerId(),
                        definition.toolName(),
                        definition.description(),
                        definition.available(),
                        definition.sideEffecting(),
                        definition.policyTags(),
                        definition.scope(),
                        schema(definition.inputSchema())))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }

  private static String schema(Map<String, Object> inputSchema) {
    return inputSchema == null ? "{}" : inputSchema.toString();
  }

  private List<Memory> selectMemories(String input) {
    if (memoryQueries == null || memoryLimit == 0 || input == null || input.isBlank()) {
      return List.of();
    }
    return memoryQueries.search(input, memoryLimit);
  }

  private static String memoryText(List<Memory> memories) {
    if (memories == null || memories.isEmpty()) {
      return "";
    }
    return memories.stream()
        .map(
            memory ->
                "- %s %s: %s; metadata=%s"
                    .formatted(memory.scope(), memory.id(), memory.text(), memory.metadata()))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }
}
