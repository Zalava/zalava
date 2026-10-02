package org.zalava.tasks.clarification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.tasks.clarification.domain.ClarificationRequest;

class ClarificationTextTest {

  private static final ClarificationRequest.Question QUESTION =
      new ClarificationRequest.Question(
          "q1",
          "Which report?",
          List.of(
              new ClarificationRequest.Choice("a", "Monthly"),
              new ClarificationRequest.Choice("b", "Weekly")),
          true);

  @Test
  void pendingTextListsChoicesAndFreeText() {
    ClarificationRequest request = request(ClarificationRequest.Status.PENDING, List.of());

    String text = ClarificationText.render(request);

    assertThat(text)
        .contains("Which report?")
        .contains("[a] Monthly")
        .contains("[b] Weekly")
        .contains("Or reply with free text.")
        .doesNotContain("Status:");
  }

  @Test
  void resolvedTextIncludesStatusAndAnswers() {
    ClarificationRequest request =
        request(
            ClarificationRequest.Status.ANSWERED,
            List.of(new ClarificationRequest.Answer("q1", null, "Monthly please")));

    String text = ClarificationText.render(request);

    assertThat(text).contains("Status: answered").contains("Q: Which report? A: Monthly please");
    assertThat(ClarificationText.answerSummary(request))
        .isEqualTo("Q: Which report? A: Monthly please");
  }

  @Test
  void resolvedTextWithoutAnswersMarksNoAnswer() {
    ClarificationRequest request = request(ClarificationRequest.Status.CANCELLED, List.of());

    assertThat(ClarificationText.render(request)).contains("Status: cancelled");
    assertThat(ClarificationText.answerSummary(request)).contains("(no answer)");
  }

  @Test
  void textIsBounded() {
    List<ClarificationRequest.Question> questions = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) {
      questions.add(
          new ClarificationRequest.Question(
              "q" + i,
              "x".repeat(2_000),
              List.of(
                  new ClarificationRequest.Choice("a", "A"),
                  new ClarificationRequest.Choice("b", "B")),
              false));
    }
    ClarificationRequest request =
        new ClarificationRequest(
            "11111111-1111-1111-1111-111111111111",
            "actor-1",
            "task-1",
            "Prompt",
            questions,
            Map.of(),
            ClarificationRequest.Status.PENDING,
            "2026-09-16T10:00:00Z",
            "2026-09-17T10:00:00Z",
            null,
            null,
            List.of());

    assertThat(ClarificationText.render(request)).endsWith("...").hasSize(4_003);
  }

  private static ClarificationRequest request(
      ClarificationRequest.Status status, List<ClarificationRequest.Answer> answers) {
    return new ClarificationRequest(
        "11111111-1111-1111-1111-111111111111",
        "actor-1",
        "task-1",
        "Prompt",
        List.of(QUESTION),
        Map.of(),
        status,
        "2026-09-16T10:00:00Z",
        "2026-09-17T10:00:00Z",
        status == ClarificationRequest.Status.ANSWERED ? Instant.now().toString() : null,
        status == ClarificationRequest.Status.PENDING ? null : Instant.now().toString(),
        answers);
  }
}
