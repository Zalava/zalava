package org.zalava.agent.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.zalava.agent.application.port.out.AgentContextAssembler;
import org.zalava.agent.domain.AgentContext;
import org.zalava.agent.domain.AgentToolSelection;
import org.zalava.memory.application.port.in.MemoryQueries;
import org.zalava.memory.domain.Memory;

public final class DefaultAgentContextAssembler implements AgentContextAssembler {
  public static final int DEFAULT_CHARACTER_BUDGET = 64_000;
  public static final int DEFAULT_MEMORY_LIMIT = 3;
  private final int characterBudget;
  private final int memoryLimit;
  private final MemoryQueries memoryQueries;
  private final KnowledgeContextEnrichment knowledge;
  private final SkillContextEnrichment skills;
  private final ContextSourceBudgets sourceBudgets;

  public DefaultAgentContextAssembler(
      int characterBudget, int memoryLimit, MemoryQueries memoryQueries) {
    this(characterBudget, memoryLimit, memoryQueries, null, null, ContextSourceBudgets.UNBOUNDED);
  }

  public DefaultAgentContextAssembler(
      int characterBudget,
      int memoryLimit,
      MemoryQueries memoryQueries,
      KnowledgeContextEnrichment knowledge) {
    this(
        characterBudget,
        memoryLimit,
        memoryQueries,
        knowledge,
        null,
        ContextSourceBudgets.UNBOUNDED);
  }

  public DefaultAgentContextAssembler(
      int characterBudget,
      int memoryLimit,
      MemoryQueries memoryQueries,
      KnowledgeContextEnrichment knowledge,
      ContextSourceBudgets sourceBudgets) {
    this(characterBudget, memoryLimit, memoryQueries, knowledge, null, sourceBudgets);
  }

  public DefaultAgentContextAssembler(
      int characterBudget,
      int memoryLimit,
      MemoryQueries memoryQueries,
      KnowledgeContextEnrichment knowledge,
      SkillContextEnrichment skills,
      ContextSourceBudgets sourceBudgets) {
    if (characterBudget < 0 || memoryLimit < 0)
      throw new IllegalArgumentException("Agent context limits must not be negative");
    this.characterBudget = characterBudget;
    this.memoryLimit = memoryLimit;
    this.memoryQueries = memoryQueries;
    this.knowledge = knowledge;
    this.skills = skills;
    this.sourceBudgets = sourceBudgets == null ? ContextSourceBudgets.UNBOUNDED : sourceBudgets;
  }

  @Override
  public AgentContext assemble(String input, AgentToolSelection selection) {
    String prompt = input == null ? "" : input;
    String boundedPrompt =
        prompt.length() <= characterBudget ? prompt : prompt.substring(0, characterBudget);
    AgentToolSelection resolved =
        selection == null ? new AgentToolSelection(List.of(), List.of(), List.of()) : selection;
    String summaries = toolSummaryText(resolved.toolSummaries());
    String boundedSummaries =
        boundedSection(
            boundedPrompt,
            "Untrusted selected SEA tool summaries:",
            summaries,
            sourceBudgets.toolSummaries());
    String promptWithSummaries =
        promptWithSection(
            boundedPrompt, "Untrusted selected SEA tool summaries:", boundedSummaries);
    String definitions = toolDefinitionText(resolved.toolDefinitions());
    String boundedDefinitions =
        boundedSection(
            promptWithSummaries,
            "Untrusted selected SEA tool definitions:",
            definitions,
            sourceBudgets.toolDefinitions());
    String promptWithDefinitions =
        promptWithSection(
            promptWithSummaries, "Untrusted selected SEA tool definitions:", boundedDefinitions);
    String memories = memoryText(selectMemories(prompt));
    String boundedMemories =
        boundedSection(
            promptWithDefinitions,
            "Untrusted selected memories:",
            memories,
            sourceBudgets.memories());
    String assembled =
        promptWithSection(promptWithDefinitions, "Untrusted selected memories:", boundedMemories);
    String knowledgeText = knowledge == null ? "" : knowledge.render(knowledge.select(prompt));
    String boundedKnowledge =
        boundedSection(
            assembled,
            "Untrusted selected knowledge evidence:",
            knowledgeText,
            sourceBudgets.knowledgeEvidence());
    assembled =
        promptWithSection(assembled, "Untrusted selected knowledge evidence:", boundedKnowledge);
    String skillText = skills == null ? "" : skills.render(skills.select());
    String boundedSkills =
        boundedSection(
            assembled, "Untrusted selected skill instructions:", skillText, sourceBudgets.skills());
    assembled =
        promptWithSection(assembled, "Untrusted selected skill instructions:", boundedSkills);
    List<AgentContext.SourceMetric> metrics = new ArrayList<>();
    metrics.add(
        new AgentContext.SourceMetric("user_prompt", prompt.length(), boundedPrompt.length()));
    if (!resolved.toolSummaries().isEmpty())
      metrics.add(
          new AgentContext.SourceMetric(
              "selected_tool_summaries", summaries.length(), boundedSummaries.length()));
    if (!resolved.toolDefinitions().isEmpty())
      metrics.add(
          new AgentContext.SourceMetric(
              "selected_tool_definitions", definitions.length(), boundedDefinitions.length()));
    if (!memories.isEmpty())
      metrics.add(
          new AgentContext.SourceMetric(
              "selected_memories", memories.length(), boundedMemories.length()));
    if (!knowledgeText.isEmpty())
      metrics.add(
          new AgentContext.SourceMetric(
              "selected_knowledge_evidence", knowledgeText.length(), boundedKnowledge.length()));
    if (!skillText.isEmpty())
      metrics.add(
          new AgentContext.SourceMetric(
              "selected_skill_instructions", skillText.length(), boundedSkills.length()));
    return new AgentContext(
        assembled, metrics.size(), characterBudget, assembled.length(), metrics);
  }

  private List<Memory> selectMemories(String input) {
    return memoryQueries == null || memoryLimit == 0 || input == null || input.isBlank()
        ? List.of()
        : memoryQueries.search(input, memoryLimit);
  }

  private String boundedSection(String prompt, String heading, String content, int sourceBudget) {
    if (content.isBlank()) return "";
    int remaining =
        characterBudget
            - prompt.length()
            - (System.lineSeparator().length() * 3)
            - heading.length();
    if (sourceBudget > 0) {
      remaining = Math.min(remaining, sourceBudget);
    }
    return remaining <= 0
        ? ""
        : content.length() <= remaining ? content : content.substring(0, remaining);
  }

  private static String promptWithSection(String prompt, String heading, String content) {
    return content.isBlank()
        ? prompt
        : prompt
            + System.lineSeparator()
            + System.lineSeparator()
            + heading
            + System.lineSeparator()
            + content;
  }

  private static String toolSummaryText(List<AgentToolSelection.ToolSummary> summaries) {
    return summaries.stream()
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
      List<AgentToolSelection.ToolDefinitionSummary> definitions) {
    return definitions.stream()
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

  private static String memoryText(List<Memory> memories) {
    return memories.stream()
        .map(
            memory ->
                "- %s %s: %s; metadata=%s"
                    .formatted(memory.scope(), memory.id(), memory.text(), memory.metadata()))
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }
}
