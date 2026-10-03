package org.zalava.tasks.clarification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A durable, actor-owned clarification request.
 *
 * <p>It is deliberately separate from {@code ZalavaToolApprovalRequests}: a clarification pauses a
 * job until the owner supplies an answer, while an approval asks the owner to permit a
 * side-effecting tool call. Model text can request a clarification but never decides it; Zalava
 * owns the typed questions, choices, persistence and single-use answer.
 */
public record ClarificationRequest(
    String requestId,
    String actorId,
    String taskReference,
    String prompt,
    List<Question> questions,
    Map<String, String> attributes,
    Status status,
    String createdAt,
    String expiresAt,
    String answeredAt,
    String resolvedAt,
    List<Answer> answers) {

  public static final int MAX_PROMPT_LENGTH = 2_000;
  public static final int MAX_QUESTIONS = 4;
  public static final int MAX_CHOICES = 4;
  public static final int MIN_CHOICES = 2;
  public static final int MAX_ANSWER_LENGTH = 2_000;

  public ClarificationRequest {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("Clarification request id is required");
    }
    if (actorId == null || actorId.isBlank()) {
      throw new IllegalArgumentException("Clarification actor id is required");
    }
    prompt = normalize(prompt, "Clarification prompt", MAX_PROMPT_LENGTH);
    questions = questions == null ? List.of() : List.copyOf(questions);
    if (questions.isEmpty() || questions.size() > MAX_QUESTIONS) {
      throw new IllegalArgumentException(
          "A clarification requires between 1 and " + MAX_QUESTIONS + " questions");
    }
    for (Question question : questions) {
      if (question == null)
        throw new IllegalArgumentException("Clarification question is required");
    }
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    status = status == null ? Status.PENDING : status;
    createdAt = requireInstant(createdAt, "createdAt");
    expiresAt = requireInstant(expiresAt, "expiresAt");
    answeredAt = optionalInstant(answeredAt, "answeredAt");
    resolvedAt = optionalInstant(resolvedAt, "resolvedAt");
    answers = answers == null ? List.of() : List.copyOf(answers);
  }

  public boolean isPending(Instant now) {
    return status == Status.PENDING && !isExpired(now);
  }

  public boolean isExpired(Instant now) {
    return expiry().isBefore(now);
  }

  public Instant expiry() {
    return Instant.parse(expiresAt);
  }

  public boolean ownedBy(String actor) {
    return actorId.equals(actor);
  }

  public ClarificationRequest answered(List<Answer> answered, Instant at) {
    validateAnswers(answered);
    return new ClarificationRequest(
        requestId,
        actorId,
        taskReference,
        prompt,
        questions,
        attributes,
        Status.ANSWERED,
        createdAt,
        expiresAt,
        at.toString(),
        at.toString(),
        answered);
  }

  public ClarificationRequest cancelled(Instant at) {
    return resolved(Status.CANCELLED, at);
  }

  public ClarificationRequest expired(Instant at) {
    return resolved(Status.EXPIRED, at);
  }

  private ClarificationRequest resolved(Status resolvedStatus, Instant at) {
    return new ClarificationRequest(
        requestId,
        actorId,
        taskReference,
        prompt,
        questions,
        attributes,
        resolvedStatus,
        createdAt,
        expiresAt,
        answeredAt,
        at.toString(),
        answers);
  }

  /** Rejects an answer that does not address exactly the asked questions with valid values. */
  public void validateAnswers(List<Answer> candidate) {
    List<Answer> safe = candidate == null ? List.of() : candidate;
    if (safe.size() != questions.size()) {
      throw new InvalidAnswerException("An answer is required for every clarification question");
    }
    for (Question question : questions) {
      Answer answer =
          safe.stream()
              .filter(value -> value.questionId().equals(question.questionId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new InvalidAnswerException(
                          "Missing answer for question " + question.questionId()));
      question.validate(answer);
    }
    for (Answer answer : safe) {
      if (questions.stream()
          .noneMatch(question -> question.questionId().equals(answer.questionId()))) {
        throw new InvalidAnswerException("Unknown clarification question " + answer.questionId());
      }
    }
  }

  private static String normalize(String value, String name, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    String normalized = value.strip();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
    }
    return normalized;
  }

  private static String requireInstant(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Clarification " + name + " is required");
    }
    Instant.parse(value);
    return value;
  }

  private static String optionalInstant(String value, String name) {
    if (value == null || value.isBlank()) return null;
    Instant.parse(value);
    return value;
  }

  public enum Status {
    PENDING,
    ANSWERED,
    CANCELLED,
    EXPIRED
  }

  public record Choice(String choiceId, String label) {
    public Choice {
      choiceId = normalize(choiceId, "Choice id", 128);
      label = normalize(label, "Choice label", 200);
    }
  }

  public record Question(
      String questionId, String prompt, List<Choice> choices, boolean freeTextAllowed) {
    public Question {
      questionId = normalize(questionId, "Question id", 128);
      prompt = normalize(prompt, "Question prompt", MAX_PROMPT_LENGTH);
      choices = choices == null ? List.of() : List.copyOf(choices);
      if (choices.size() < MIN_CHOICES || choices.size() > MAX_CHOICES) {
        throw new IllegalArgumentException(
            "A clarification question requires between "
                + MIN_CHOICES
                + " and "
                + MAX_CHOICES
                + " choices");
      }
      long distinctIds = choices.stream().map(Choice::choiceId).distinct().count();
      if (distinctIds != choices.size()) {
        throw new IllegalArgumentException("Clarification choice ids must be unique");
      }
    }

    public void validate(Answer answer) {
      boolean hasChoice = answer.choiceId() != null && !answer.choiceId().isBlank();
      boolean hasText = answer.freeText() != null && !answer.freeText().isBlank();
      if (!hasChoice && !hasText) {
        throw new InvalidAnswerException(
            "Clarification question " + questionId + " requires a choice or free text");
      }
      if (hasChoice
          && choices.stream().noneMatch(choice -> choice.choiceId().equals(answer.choiceId()))) {
        throw new InvalidAnswerException("Unknown clarification choice " + answer.choiceId());
      }
      if (hasText && !freeTextAllowed) {
        throw new InvalidAnswerException(
            "Clarification question " + questionId + " does not accept free text");
      }
      if (hasText && answer.freeText().strip().length() > MAX_ANSWER_LENGTH) {
        throw new InvalidAnswerException(
            "Clarification free text exceeds " + MAX_ANSWER_LENGTH + " characters");
      }
    }

    public Choice choice(String choiceId) {
      return choices.stream()
          .filter(choice -> choice.choiceId().equals(choiceId))
          .findFirst()
          .orElseThrow(
              () -> new InvalidAnswerException("Unknown clarification choice " + choiceId));
    }
  }

  public record Answer(String questionId, String choiceId, String freeText) {
    public Answer {
      if (questionId == null || questionId.isBlank()) {
        throw new InvalidAnswerException("Clarification answer question id is required");
      }
      questionId = questionId.strip();
      choiceId = choiceId == null || choiceId.isBlank() ? null : choiceId.strip();
      freeText = freeText == null || freeText.isBlank() ? null : freeText.strip();
    }

    /** A normalized, display-safe answer value. */
    public String value(Question question) {
      if (choiceId != null) {
        return question.choice(choiceId).label();
      }
      return freeText;
    }
  }

  public static final class InvalidAnswerException extends RuntimeException {
    public InvalidAnswerException(String message) {
      super(message);
    }
  }
}
