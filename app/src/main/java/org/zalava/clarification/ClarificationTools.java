package org.zalava.clarification;

import java.util.LinkedHashMap;
import java.util.Map;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.Actor;
import org.zalava.clarification.domain.ClarificationDraft;
import org.zalava.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.ObjectMapper;

/**
 * Model-facing adapter that lets an actor-owned background job request typed clarification. It only
 * records the request; SEA validates the schema, persists it and pauses the loop. The model never
 * answers or decides a clarification.
 */
public final class ClarificationTools {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SeaClarifications clarifications;
  private final ActorExecutionContext actorContext;
  private final TaskExecutionContext taskContext;

  public ClarificationTools(
      SeaClarifications clarifications,
      ActorExecutionContext actorContext,
      TaskExecutionContext taskContext) {
    this.clarifications = clarifications;
    this.actorContext = actorContext;
    this.taskContext = taskContext;
  }

  @Tool(
      name = "requestClarification",
      description =
          "Asks the job owner one typed question with two to four choices (and optional free text) and pauses the job until the owner answers. Use only when the task cannot proceed without an owner decision. It never answers the question, never grants approval and never changes job authority.")
  public String request(
      @ToolParam(
              description =
                  "The clarification: a bounded prompt and one or more questions, each with two to four choices and whether free text is allowed")
          ClarificationDraft clarification) {
    Actor actor = actorContext.currentPrincipal().map(principal -> principal.actor()).orElse(null);
    ActorTaskExecutionReference execution = taskContext.currentActorTaskReference().orElse(null);
    if (actor == null || execution == null) {
      return error("Clarification is only available inside an owner-scoped background job.");
    }
    try {
      ClarificationRequest request =
          clarifications.create(actor, execution.taskReference(), clarification);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("requestId", request.requestId());
      result.put("status", request.status().name());
      result.put("prompt", request.prompt());
      result.put(
          "questions",
          request.questions().stream()
              .map(
                  question ->
                      Map.of(
                          "questionId", question.questionId(),
                          "prompt", question.prompt(),
                          "choices",
                              question.choices().stream()
                                  .map(choice -> choice.choiceId() + ": " + choice.label())
                                  .toList(),
                          "freeTextAllowed", question.freeTextAllowed()))
              .toList());
      result.put(
          "nextAction", "Stop here and report awaiting_human_input. Do not guess the answer.");
      return json(result);
    } catch (RuntimeException exception) {
      return error("SEA could not record the clarification request.");
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
            "Continue without it or fail the task."));
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize clarification result", exception);
    }
  }
}
