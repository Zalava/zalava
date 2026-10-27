package org.zalava.web.ui;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

/** Owner-authorized, bounded evidence for one conversation. */
@RestController
public final class ConversationEvidenceController {
  private final ActorChatQueries conversations;
  private final AuthenticatedActorResolver actors;
  private final AgentRunQueries runs;
  private final InvocationLogQueries tools;
  private final ModelBoundary boundary;

  public ConversationEvidenceController(
      ActorChatQueries conversations,
      AuthenticatedActorResolver actors,
      AgentRunQueries runs,
      InvocationLogQueries tools,
      ModelBoundary boundary) {
    this.conversations = conversations;
    this.actors = actors;
    this.runs = runs;
    this.tools = tools;
    this.boundary = boundary;
  }

  @GetMapping("/chat/{conversationId}/evidence")
  public Evidence evidence(@PathVariable String conversationId, Authentication authentication) {
    var actor = actors.actor(authentication);
    var reference = new ConversationReference(conversationId);
    if (!conversations.conversations(actor).contains(reference))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    String internalId = new ActorConversationId(actor, reference).value();
    return new Evidence(
        runs.recent().stream()
            .filter(run -> run.conversationId().equals(internalId))
            .sorted(Comparator.comparing(AgentRun::startedAt).reversed())
            .limit(50)
            .map(
                run ->
                    new Run(
                        run.id(),
                        run.startedAt().toString(),
                        run.completedAt().toString(),
                        run.status().name(),
                        run.durationMillis(),
                        run.selectedToolCount(),
                        run.contextCharactersUsed(),
                        run.contextCharacterBudget(),
                        boundary.redact(run.promptPreview()),
                        boundary.redact(run.resultPreview()),
                        boundary.redact(run.errorPreview()),
                        run.contextSourceMetrics()))
            .toList(),
        tools.recentEntries().stream()
            .filter(
                tool ->
                    internalId.equals(tool.conversationId())
                        && actor.accountId().toString().equals(tool.actorId()))
            .limit(50)
            .map(
                tool ->
                    new Tool(
                        tool.timestamp(),
                        tool.providerId(),
                        tool.toolName(),
                        tool.success(),
                        tool.durationMillis(),
                        boundary.redact(tool.errorMessage()),
                        boundary.redact(tool.resultPreview()),
                        boundary.redact(tool.inputPreview()),
                        tool.runId()))
            .toList());
  }

  public record Evidence(List<Run> runs, List<Tool> tools) {}

  public record Run(
      String id,
      String at,
      String completedAt,
      String status,
      long durationMillis,
      int selectedToolCount,
      int contextCharactersUsed,
      int contextCharacterBudget,
      String prompt,
      String result,
      String error,
      List<AgentRun.ContextSourceMetric> contextSources) {}

  public record Tool(
      String at,
      String provider,
      String name,
      boolean success,
      long durationMillis,
      String error,
      String result,
      String input,
      String runId) {}
}
