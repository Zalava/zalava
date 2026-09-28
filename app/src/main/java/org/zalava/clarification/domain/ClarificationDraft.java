package org.zalava.clarification.domain;

import java.util.List;

/** Bounded model-supplied draft that SEA validates before persisting a clarification request. */
public record ClarificationDraft(String prompt, List<Question> questions) {

  public ClarificationDraft {
    questions = questions == null ? List.of() : List.copyOf(questions);
  }

  public List<ClarificationRequest.Question> toQuestions() {
    return questions.stream()
        .map(
            question ->
                new ClarificationRequest.Question(
                    question.questionId(),
                    question.prompt(),
                    question.choices().stream()
                        .map(
                            choice ->
                                new ClarificationRequest.Choice(choice.choiceId(), choice.label()))
                        .toList(),
                    question.freeTextAllowed()))
        .toList();
  }

  public record Question(
      String questionId, String prompt, List<Choice> choices, boolean freeTextAllowed) {
    public Question {
      choices = choices == null ? List.of() : List.copyOf(choices);
    }
  }

  public record Choice(String choiceId, String label) {}
}
