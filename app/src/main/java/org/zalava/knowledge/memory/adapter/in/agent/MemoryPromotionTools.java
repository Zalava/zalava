package org.zalava.knowledge.memory.adapter.in.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.memory.application.ZalavaMemoryPromotions;
import org.zalava.knowledge.memory.application.port.in.MemoryPromotions;
import org.zalava.knowledge.memory.domain.MemoryContentPolicy;
import org.zalava.knowledge.memory.domain.MemoryProposal;
import org.zalava.knowledge.memory.domain.MemoryProposalDraft;
import tools.jackson.databind.ObjectMapper;

/**
 * Model-facing adapter that lets the current actor's model propose durable memory.
 *
 * <p>It only records a pending proposal under the trusted actor identity: Zalava validates scope
 * and content, the proposal stays reviewable, and promotion requires an explicit owner approval
 * through the Zalava review surface. The model never writes, approves, consolidates or revokes
 * durable memory.
 */
public final class MemoryPromotionTools {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final MemoryPromotions promotions;
  private final ActorExecutionContext actorContext;

  public MemoryPromotionTools(MemoryPromotions promotions, ActorExecutionContext actorContext) {
    this.promotions = promotions;
    this.actorContext = actorContext;
  }

  @Tool(
      name = "proposeMemory",
      description =
          "Proposes a durable fact, decision or lesson for the owner to review. It creates a pending proposal only: it never writes durable memory, never approves its own proposal and never changes authority. Use a durable scope (user, project, agent) and never include credentials or transient run details.")
  public String propose(
      @ToolParam(
              description =
                  "The durable-memory proposal: a durable scope, the text to remember, optional metadata and an optional source reference")
          MemoryProposalDraft proposal) {
    Actor actor = actorContext.currentPrincipal().map(principal -> principal.actor()).orElse(null);
    if (actor == null) {
      return error("Memory proposals require an authenticated owner.");
    }
    try {
      MemoryProposal created = promotions.propose(actor, proposal);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("proposalId", created.id());
      result.put("status", created.status().name());
      result.put(
          "nextAction", "Report the pending proposal id and wait for the owner to review it.");
      return json(result);
    } catch (ZalavaMemoryPromotions.DuplicateMemoryException exception) {
      return error("An identical durable memory already exists.");
    } catch (MemoryContentPolicy.UnsafeMemoryContentException exception) {
      return error(exception.getMessage());
    } catch (IllegalArgumentException exception) {
      return error("Zalava rejected the memory proposal: " + exception.getMessage());
    } catch (RuntimeException exception) {
      return error("Zalava could not record the memory proposal.");
    }
  }

  private static String error(String message) {
    return json(
        Map.of(
            "status",
            "REJECTED",
            "message",
            message,
            "nextAction",
            "Continue without it; do not write memory directly."));
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize memory proposal result", exception);
    }
  }
}
