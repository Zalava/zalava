package org.zalava.agent.application;

/**
 * Optional per-source character caps applied on top of the aggregate context budget.
 *
 * <p>A cap of {@code 0} means the source is bounded only by the aggregate budget, which preserves
 * the pre-CTX-02 behavior.
 */
public record ContextSourceBudgets(
    int toolSummaries, int toolDefinitions, int memories, int knowledgeEvidence, int skills) {

  public static final ContextSourceBudgets UNBOUNDED = new ContextSourceBudgets(0, 0, 0, 0, 0);

  public ContextSourceBudgets(
      int toolSummaries, int toolDefinitions, int memories, int knowledgeEvidence) {
    this(toolSummaries, toolDefinitions, memories, knowledgeEvidence, 0);
  }

  public ContextSourceBudgets {
    if (toolSummaries < 0
        || toolDefinitions < 0
        || memories < 0
        || knowledgeEvidence < 0
        || skills < 0) {
      throw new IllegalArgumentException("Context source budgets must not be negative");
    }
  }
}
