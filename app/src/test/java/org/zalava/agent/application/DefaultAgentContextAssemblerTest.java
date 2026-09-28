package org.zalava.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.domain.AgentContext;
import org.zalava.agent.domain.AgentToolSelection;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.port.out.KnowledgeEvidenceStore;
import org.zalava.knowledge.domain.KnowledgeEvidence;
import org.zalava.memory.application.port.in.MemoryQueries;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryScope;
import org.zalava.skills.application.SkillActivationMetrics;
import org.zalava.skills.application.port.in.SkillActivations;
import org.zalava.skills.domain.SkillActivation;
import org.junit.jupiter.api.Test;

/**
 * Covers the branch paths of the port-based agent context assembler: budget truncation,
 * empty-selection and null-selection handling, memory gating, and section-boundary behavior.
 */
class DefaultAgentContextAssemblerTest {

  @Test
  void rejectsNegativeLimits() {
    assertThatThrownBy(() -> new DefaultAgentContextAssembler(-1, 3, queries()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent context limits must not be negative");
    assertThatThrownBy(() -> new DefaultAgentContextAssembler(100, -1, queries()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent context limits must not be negative");
  }

  @Test
  void treatsNullInputAndNullSelectionAsEmptyContextSources() {
    var assembler = new DefaultAgentContextAssembler(64_000, 3, null);

    AgentContext context = assembler.assemble(null, null);

    assertThat(context.prompt()).isEmpty();
    assertThat(context.sourceCount()).isEqualTo(1);
    assertThat(context.sourceMetrics())
        .singleElement()
        .satisfies(
            metric -> {
              assertThat(metric.sourceType()).isEqualTo("user_prompt");
              assertThat(metric.charactersAvailable()).isZero();
              assertThat(metric.charactersUsed()).isZero();
            });
    assertThat(context.characterBudget()).isEqualTo(64_000);
  }

  @Test
  void truncatesAnOverBudgetPrompt() {
    var assembler = new DefaultAgentContextAssembler(10, 0, queries());

    AgentContext context = assembler.assemble("abcdefghijk", null);

    assertThat(context.prompt()).isEqualTo("abcdefghij");
    assertThat(context.charactersUsed()).isEqualTo(10);
  }

  @Test
  void omitsSectionMetricsWhenTheSelectionHasNoSummariesOrDefinitions() {
    var assembler = new DefaultAgentContextAssembler(500, 3, queries(memory("remembered")));

    AgentContext context =
        assembler.assemble("query", new AgentToolSelection(List.of(), List.of(), List.of()));

    assertThat(context.prompt()).contains("Untrusted selected memories:").contains("remembered");
    assertThat(context.sourceMetrics())
        .extracting(AgentContext.SourceMetric::sourceType)
        .containsExactly("user_prompt", "selected_memories");
  }

  @Test
  void rendersSummariesDefinitionsAndMemoriesWithAllFields() {
    var assembler =
        new DefaultAgentContextAssembler(
            4_000, 2, queries(memory("workflow memory"), memory("second memory")));

    AgentContext context =
        assembler.assemble(
            "plan the work",
            new AgentToolSelection(
                List.of(),
                List.of(
                    new AgentToolSelection.ToolSummary(
                        "shopping-list",
                        "addItem",
                        "Add an item",
                        true,
                        List.of("sea_backed"),
                        Map.of("list", "active"))),
                List.of(
                    new AgentToolSelection.ToolDefinitionSummary(
                        "shopping-list",
                        "addItem",
                        "Add an item",
                        true,
                        List.of("sea_backed"),
                        Map.of("list", "active"),
                        Map.of("type", "object"),
                        true))));

    assertThat(context.prompt())
        .contains(
            "shopping-list/addItem: Add an item; sideEffects=true; policyTags=[sea_backed]; scope={list=active}")
        .contains("inputSchema={type=object}")
        .contains("PROJECT memory-1: workflow memory; metadata={source=test}");
    assertThat(context.sourceMetrics())
        .extracting(AgentContext.SourceMetric::sourceType)
        .containsExactly(
            "user_prompt",
            "selected_tool_summaries",
            "selected_tool_definitions",
            "selected_memories");
  }

  @Test
  void skipsMemoryLookupForBlankPrompts() {
    CountingQueries countingQueries = new CountingQueries();
    var assembler = new DefaultAgentContextAssembler(100, 3, countingQueries);

    AgentContext blank = assembler.assemble("   ", null);

    assertThat(countingQueries.searchCalls).isZero();
    assertThat(blank.sourceMetrics()).hasSize(1);

    assembler.assemble("real query", null);

    assertThat(countingQueries.searchCalls).isEqualTo(1);
  }

  @Test
  void truncatesAnOverBudgetPromptBeforeSectionAssembly() {
    var assembler = new DefaultAgentContextAssembler(100, 3, new CountingQueries());

    AgentContext overBudget = assembler.assemble("x".repeat(150), null);

    assertThat(overBudget.prompt()).hasSize(100);
    assertThat(overBudget.charactersUsed()).isEqualTo(100);
  }

  @Test
  void dropsTrailingSectionsOnceTheBudgetIsExhausted() {
    var assembler = new DefaultAgentContextAssembler(60, 3, queries(memory("m".repeat(200))));

    AgentContext context =
        assembler.assemble(
            "seed prompt",
            new AgentToolSelection(
                List.of(),
                List.of(
                    new AgentToolSelection.ToolSummary(
                        "p", "t", "d".repeat(200), false, List.of(), Map.of())),
                List.of()));

    assertThat(context.prompt()).hasSizeLessThanOrEqualTo(60);
    assertThat(context.charactersUsed()).isEqualTo(context.prompt().length());
  }

  @Test
  void rendersDefinitionsWithMapSchemasInline() {
    var assembler = new DefaultAgentContextAssembler(2_000, 0, null);

    AgentContext context =
        assembler.assemble(
            "prompt",
            new AgentToolSelection(
                List.of(),
                List.of(),
                List.of(
                    new AgentToolSelection.ToolDefinitionSummary(
                        "p",
                        "t",
                        "d",
                        false,
                        List.of(),
                        Map.of(),
                        Map.of("type", "object"),
                        false))));

    assertThat(context.prompt()).contains("inputSchema={type=object}");
    assertThat(context.sourceMetrics())
        .extracting(AgentContext.SourceMetric::sourceType)
        .containsExactly("user_prompt", "selected_tool_definitions");
  }

  @Test
  void boundsKnowledgeEvidenceInsideTheTotalContextBudget() {
    KnowledgeEvidenceStore store = org.mockito.Mockito.mock(KnowledgeEvidenceStore.class);
    var actors = new ActorExecutionContext();
    var actor = new Actor(AccountId.newId());
    KnowledgeEvidence evidence =
        new KnowledgeEvidence(
            java.util.UUID.randomUUID(), 1, "guide", "text/plain", "x".repeat(500), false);
    org.mockito.Mockito.when(store.search(actor, "query", 3)).thenReturn(List.of(evidence));
    var knowledge =
        new KnowledgeContextEnrichment(
            new KnowledgeEvidenceQueries(store), actors, new ModelBoundary(1_000, ""));
    var assembler = new DefaultAgentContextAssembler(120, 0, null, knowledge);

    AgentContext context =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                assembler.assemble(
                    "query", new AgentToolSelection(List.of(), List.of(), List.of())));

    assertThat(context.prompt()).contains("Untrusted selected knowledge evidence:");
    assertThat(context.charactersUsed()).isLessThanOrEqualTo(120);
    assertThat(context.sourceMetrics())
        .extracting(AgentContext.SourceMetric::sourceType)
        .contains("selected_knowledge_evidence");
    assertThat(
            context.sourceMetrics().stream()
                .filter(metric -> metric.sourceType().equals("selected_knowledge_evidence"))
                .findFirst()
                .orElseThrow()
                .charactersUsed())
        .isLessThan(500);
  }

  @Test
  void leavesExplicitToolContextUnchangedWhenKnowledgeEnrichmentIsDisabled() {
    KnowledgeEvidenceStore store = org.mockito.Mockito.mock(KnowledgeEvidenceStore.class);
    var actors = new ActorExecutionContext();
    var disabledKnowledge =
        new KnowledgeContextEnrichment(
            new KnowledgeEvidenceQueries(store), actors, new ModelBoundary(1_000, ""), false);
    AgentToolSelection selection =
        new AgentToolSelection(
            List.of(),
            List.of(
                new AgentToolSelection.ToolSummary(
                    "provider", "lookup", "explicit", false, List.of(), Map.of())),
            List.of());
    var withoutKnowledge = new DefaultAgentContextAssembler(500, 0, null);
    var disabled = new DefaultAgentContextAssembler(500, 0, null, disabledKnowledge);

    AgentContext expected = withoutKnowledge.assemble("query", selection);
    AgentContext actual = disabled.assemble("query", selection);

    assertThat(actual).isEqualTo(expected);
    org.mockito.Mockito.verifyNoInteractions(store);
  }

  @Test
  void enforcesExplicitPerSourceBudgetsOnTopOfTheAggregateBudget() {
    var assembler =
        new DefaultAgentContextAssembler(
            5_000,
            3,
            queries(memory("m".repeat(200))),
            null,
            new ContextSourceBudgets(40, 60, 30, 0));

    AgentContext context =
        assembler.assemble(
            "query",
            new AgentToolSelection(
                List.of(),
                List.of(
                    new AgentToolSelection.ToolSummary(
                        "p", "t", "d".repeat(200), false, List.of(), Map.of())),
                List.of(
                    new AgentToolSelection.ToolDefinitionSummary(
                        "p",
                        "t",
                        "d".repeat(200),
                        false,
                        List.of(),
                        Map.of(),
                        Map.of("type", "object"),
                        true))));

    assertThat(metric(context, "selected_tool_summaries").charactersUsed()).isEqualTo(40);
    assertThat(metric(context, "selected_tool_definitions").charactersUsed()).isEqualTo(60);
    assertThat(metric(context, "selected_memories").charactersUsed()).isEqualTo(30);
    assertThat(context.charactersUsed()).isLessThanOrEqualTo(5_000);
  }

  @Test
  void treatsZeroSourceBudgetsAsAggregateOnly() {
    var assembler =
        new DefaultAgentContextAssembler(
            5_000, 3, queries(memory("m".repeat(50))), null, ContextSourceBudgets.UNBOUNDED);

    AgentContext context =
        assembler.assemble(
            "query",
            new AgentToolSelection(
                List.of(),
                List.of(
                    new AgentToolSelection.ToolSummary(
                        "p", "t", "d".repeat(50), false, List.of(), Map.of())),
                List.of()));

    assertThat(metric(context, "selected_tool_summaries").charactersUsed())
        .isEqualTo(metric(context, "selected_tool_summaries").charactersAvailable());
    assertThat(metric(context, "selected_memories").charactersUsed())
        .isEqualTo(metric(context, "selected_memories").charactersAvailable());
  }

  @Test
  void rejectsNegativePerSourceBudgets() {
    assertThatThrownBy(() -> new ContextSourceBudgets(-1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Context source budgets must not be negative");
  }

  @Test
  void injectsActiveSkillInstructionsAsUntrustedAndNeverAddsTools() {
    var actors = new ActorExecutionContext();
    var actor = new Actor(AccountId.newId());
    SkillActivation activation =
        SkillActivation.activated(
            actor.accountId().toString(),
            "test-skill",
            "1.0.0",
            "digest",
            "step one\nstep two",
            "now");
    var skills =
        new SkillContextEnrichment(activations(activation), actors, new ModelBoundary(1_000, ""));
    var assembler =
        new DefaultAgentContextAssembler(
            500, 0, null, null, skills, ContextSourceBudgets.UNBOUNDED);

    AgentContext context =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                assembler.assemble(
                    "query", new AgentToolSelection(List.of(), List.of(), List.of())));

    assertThat(context.prompt())
        .contains("Untrusted selected skill instructions:")
        .contains("skill=test-skill@1.0.0")
        .contains("step one");
    assertThat(context.sourceMetrics())
        .extracting(AgentContext.SourceMetric::sourceType)
        .containsExactly("user_prompt", "selected_skill_instructions");
    assertThat(metric(context, "selected_skill_instructions").charactersUsed()).isPositive();
  }

  @Test
  void boundsSkillInstructionsInsideTheTotalAndPerSourceBudget() {
    var actors = new ActorExecutionContext();
    var actor = new Actor(AccountId.newId());
    SkillActivation activation =
        SkillActivation.activated(
            actor.accountId().toString(), "test-skill", "1.0.0", "digest", "x".repeat(500), "now");
    var skills =
        new SkillContextEnrichment(activations(activation), actors, new ModelBoundary(5_000, ""));
    var assembler =
        new DefaultAgentContextAssembler(
            120, 0, null, null, skills, new ContextSourceBudgets(0, 0, 0, 0, 30));

    AgentContext context =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                assembler.assemble(
                    "query", new AgentToolSelection(List.of(), List.of(), List.of())));

    assertThat(context.prompt()).contains("Untrusted selected skill instructions:");
    assertThat(metric(context, "selected_skill_instructions").charactersUsed()).isEqualTo(30);
    assertThat(context.charactersUsed()).isLessThanOrEqualTo(120);
  }

  @Test
  void leavesContextUnchangedWhenSkillEnrichmentIsDisabled() {
    var actors = new ActorExecutionContext();
    var actor = new Actor(AccountId.newId());
    SkillActivations ignored = org.mockito.Mockito.mock(SkillActivations.class);
    var disabled = new SkillContextEnrichment(ignored, actors, new ModelBoundary(1_000, ""), false);
    var withoutSkills = new DefaultAgentContextAssembler(500, 0, null);
    var withDisabledSkills =
        new DefaultAgentContextAssembler(
            500, 0, null, null, disabled, ContextSourceBudgets.UNBOUNDED);

    AgentContext expected =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                withoutSkills.assemble(
                    "query", new AgentToolSelection(List.of(), List.of(), List.of())));
    AgentContext actual =
        actors.call(
            actor,
            AccountRole.MEMBER,
            () ->
                withDisabledSkills.assemble(
                    "query", new AgentToolSelection(List.of(), List.of(), List.of())));

    assertThat(actual).isEqualTo(expected);
    org.mockito.Mockito.verifyNoInteractions(ignored);
  }

  private static SkillActivations activations(SkillActivation... active) {
    return new SkillActivations() {
      @Override
      public SkillActivation activate(
          Actor actor, AccountRole role, String name, String expectedVersion) {
        throw new UnsupportedOperationException("activate is not used by this test");
      }

      @Override
      public SkillActivation deactivate(Actor actor, String name) {
        throw new UnsupportedOperationException("deactivate is not used by this test");
      }

      @Override
      public List<SkillActivation> active(Actor actor) {
        return List.of(active);
      }

      @Override
      public java.util.Optional<SkillActivation> find(Actor actor, String name) {
        return java.util.Optional.empty();
      }

      @Override
      public SkillActivationMetrics metrics() {
        return SkillActivationMetrics.EMPTY;
      }
    };
  }

  private static AgentContext.SourceMetric metric(AgentContext context, String sourceType) {
    return context.sourceMetrics().stream()
        .filter(metric -> metric.sourceType().equals(sourceType))
        .findFirst()
        .orElseThrow();
  }

  private static MemoryQueries queries(Memory... memories) {
    return new MemoryQueries() {
      @Override
      public List<Memory> recent(int limit) {
        throw new UnsupportedOperationException("recent is not used by this test");
      }

      @Override
      public List<Memory> search(String query, int limit) {
        return java.util.Arrays.stream(memories).limit(limit).toList();
      }
    };
  }

  private static final class CountingQueries implements MemoryQueries {
    int searchCalls;

    @Override
    public List<Memory> recent(int limit) {
      throw new UnsupportedOperationException("recent is not used by this test");
    }

    @Override
    public List<Memory> search(String query, int limit) {
      searchCalls++;
      return List.of();
    }
  }

  private static Memory memory(String text) {
    return new Memory(
        "memory-1", MemoryScope.PROJECT, text, Map.of("source", "test"), Instant.EPOCH);
  }
}
