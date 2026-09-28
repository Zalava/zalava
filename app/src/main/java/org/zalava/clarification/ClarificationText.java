package org.zalava.clarification;

import java.util.stream.Collectors;
import org.zalava.clarification.domain.ClarificationRequest;

/**
 * Bounded safe-text rendering for a clarification request. It contains only SEA-validated prompts,
 * choice labels and owner-supplied answers, so a non-web channel can deliver it without leaking
 * model reasoning, tool arguments or private state.
 */
public final class ClarificationText {
  private ClarificationText() {}

  private static final int MAX_RENDERED_LENGTH = 4_000;

  public static String render(ClarificationRequest request) {
    StringBuilder text = new StringBuilder();
    text.append("SEA needs clarification (request ")
        .append(request.requestId())
        .append("): ")
        .append(request.prompt());
    for (ClarificationRequest.Question question : request.questions()) {
      text.append(System.lineSeparator()).append(question.prompt());
      for (ClarificationRequest.Choice choice : question.choices()) {
        text.append(System.lineSeparator())
            .append("  [")
            .append(choice.choiceId())
            .append("] ")
            .append(choice.label());
      }
      if (question.freeTextAllowed()) {
        text.append(System.lineSeparator()).append("  Or reply with free text.");
      }
    }
    if (request.status() != ClarificationRequest.Status.PENDING) {
      text.append(System.lineSeparator())
          .append("Status: ")
          .append(request.status().name().toLowerCase());
      String answers = answerSummary(request);
      if (!answers.isBlank()) {
        text.append(System.lineSeparator()).append(answers);
      }
    }
    return bounded(text.toString());
  }

  /** One bounded "Q: ... A: ..." line per question, suitable for a task-loop prompt. */
  public static String answerSummary(ClarificationRequest request) {
    return request.questions().stream()
        .map(
            question -> {
              String answer =
                  request.answers().stream()
                      .filter(value -> value.questionId().equals(question.questionId()))
                      .findFirst()
                      .map(value -> value.value(question))
                      .orElse("(no answer)");
              return "Q: " + question.prompt() + " A: " + answer;
            })
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private static String bounded(String value) {
    return value.length() <= MAX_RENDERED_LENGTH
        ? value
        : value.substring(0, MAX_RENDERED_LENGTH) + "...";
  }
}
