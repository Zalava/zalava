package org.zalava.tasks.clarification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClarificationRequestTest {

  private static final List<ClarificationRequest.Choice> CHOICES =
      List.of(
          new ClarificationRequest.Choice("a", "Monthly"),
          new ClarificationRequest.Choice("b", "Weekly"));

  @Test
  void validRequestsExposeStatusExpiryAndOwnership() {
    ClarificationRequest request =
        request("actor-1", List.of(question("q1", false)), ClarificationRequest.Status.PENDING);

    assertThat(request.ownedBy("actor-1")).isTrue();
    assertThat(request.ownedBy("actor-2")).isFalse();
    assertThat(request.isPending(Instant.parse("2026-09-16T10:00:00Z"))).isTrue();
    assertThat(request.isExpired(Instant.parse("2026-09-17T11:00:00Z"))).isTrue();
    assertThat(request.questions()).hasSize(1);
  }

  @Test
  void requestIdentifiersAndActorsAreRequired() {
    assertThatThrownBy(() -> requestBuilder().requestId(" ").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("request id");
    assertThatThrownBy(() -> requestBuilder().actorId(null).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actor id");
  }

  @Test
  void promptAndQuestionBoundsAreEnforced() {
    assertThatThrownBy(() -> requestBuilder().prompt(" ").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prompt");
    assertThatThrownBy(() -> requestBuilder().prompt("x".repeat(2_001)).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds");

    assertThatThrownBy(() -> request("actor-1", List.of(), ClarificationRequest.Status.PENDING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and");
    assertThatThrownBy(
            () ->
                request(
                    "actor-1",
                    List.of(
                        question("q1", false),
                        question("q2", false),
                        question("q3", false),
                        question("q4", false),
                        question("q5", false)),
                    ClarificationRequest.Status.PENDING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and");
  }

  @Test
  void questionsRequireTwoToFourDistinctChoices() {
    assertThatThrownBy(() -> new ClarificationRequest.Question("q1", "Which?", List.of(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2 and");
    assertThatThrownBy(
            () ->
                new ClarificationRequest.Question(
                    "q1",
                    "Which?",
                    List.of(
                        new ClarificationRequest.Choice("a", "A"),
                        new ClarificationRequest.Choice("a", "Again")),
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
  }

  @Test
  void answersMustMatchEveryQuestionAndRespectTheFreeTextPolicy() {
    ClarificationRequest request =
        request("actor-1", List.of(question("q1", false)), ClarificationRequest.Status.PENDING);

    assertThatThrownBy(() -> request.validateAnswers(List.of()))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("every clarification question");
    assertThatThrownBy(
            () ->
                request.validateAnswers(
                    List.of(new ClarificationRequest.Answer("missing", "a", null))))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("Missing answer");
    assertThatThrownBy(
            () ->
                request.validateAnswers(List.of(new ClarificationRequest.Answer("q1", "zz", null))))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("Unknown clarification choice");
    assertThatThrownBy(
            () ->
                request.validateAnswers(
                    List.of(new ClarificationRequest.Answer("q1", null, "free text"))))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("does not accept free text");
    assertThatThrownBy(
            () ->
                request.validateAnswers(List.of(new ClarificationRequest.Answer("q1", null, null))))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("requires a choice or free text");
  }

  @Test
  void anAnswerMayUseFreeTextWhenAllowedAndRendersItsValue() {
    ClarificationRequest.Question question = question("q1", true);
    ClarificationRequest request =
        request("actor-1", List.of(question), ClarificationRequest.Status.PENDING);

    request.validateAnswers(
        List.of(new ClarificationRequest.Answer("q1", null, "  Monthly please  ")));

    ClarificationRequest.Answer freeText =
        new ClarificationRequest.Answer("q1", null, "  Monthly please  ");
    assertThat(freeText.value(question)).isEqualTo("Monthly please");
    ClarificationRequest.Answer chosen = new ClarificationRequest.Answer("q1", "a", null);
    assertThat(chosen.value(question)).isEqualTo("Monthly");
  }

  @Test
  void overlongFreeTextIsRejected() {
    ClarificationRequest.Question question = question("q1", true);
    ClarificationRequest request =
        request("actor-1", List.of(question), ClarificationRequest.Status.PENDING);

    assertThatThrownBy(
            () ->
                request.validateAnswers(
                    List.of(new ClarificationRequest.Answer("q1", null, "x".repeat(2_001)))))
        .isInstanceOf(ClarificationRequest.InvalidAnswerException.class)
        .hasMessageContaining("free text exceeds");
  }

  @Test
  void answersAreOnlyAddressableUntilTheRequestIsPending() {
    ClarificationRequest request =
        request("actor-1", List.of(question("q1", false)), ClarificationRequest.Status.PENDING);

    ClarificationRequest answered =
        request.answered(
            List.of(new ClarificationRequest.Answer("q1", "a", null)),
            Instant.parse("2026-09-16T11:00:00Z"));

    assertThat(answered.status()).isEqualTo(ClarificationRequest.Status.ANSWERED);
    assertThat(answered.answeredAt()).isEqualTo("2026-09-16T11:00:00Z");
    assertThat(answered.cancelled(Instant.parse("2026-09-16T12:00:00Z")).status())
        .isEqualTo(ClarificationRequest.Status.CANCELLED);
    assertThat(answered.expired(Instant.parse("2026-09-16T12:00:00Z")).status())
        .isEqualTo(ClarificationRequest.Status.EXPIRED);
  }

  @Test
  void timestampFieldsAreValidated() {
    assertThatThrownBy(() -> requestBuilder().createdAt(" ").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createdAt");
    assertThatThrownBy(() -> requestBuilder().expiresAt("not-a-time").build())
        .isInstanceOf(java.time.DateTimeException.class);
    assertThatThrownBy(() -> requestBuilder().answeredAt("not-a-time").build())
        .isInstanceOf(java.time.DateTimeException.class);

    ClarificationRequest request = requestBuilder().answeredAt("2026-09-16T11:00:00Z").build();
    assertThat(request.answeredAt()).isEqualTo("2026-09-16T11:00:00Z");
    assertThat(requestBuilder().answeredAt(null).build().answeredAt()).isNull();
  }

  @Test
  void draftConvertsToValidatedQuestions() {
    ClarificationDraft draft =
        new ClarificationDraft(
            "Which?",
            List.of(
                new ClarificationDraft.Question(
                    "q1",
                    "Which?",
                    List.of(
                        new ClarificationDraft.Choice("a", "A"),
                        new ClarificationDraft.Choice("b", "B")),
                    true)));

    assertThat(draft.toQuestions()).hasSize(1);
    assertThat(draft.toQuestions().getFirst().freeTextAllowed()).isTrue();
    assertThat(new ClarificationDraft("Which?", null).questions()).isEmpty();
    assertThat(new ClarificationDraft.Question("q1", "Which?", null, false).choices()).isEmpty();
  }

  private static ClarificationRequest.Question question(String id, boolean freeText) {
    return new ClarificationRequest.Question(id, "Which report?", CHOICES, freeText);
  }

  private static ClarificationRequest request(
      String actorId,
      List<ClarificationRequest.Question> questions,
      ClarificationRequest.Status status) {
    return requestBuilder().actorId(actorId).questions(questions).status(status).build();
  }

  private static RequestBuilder requestBuilder() {
    return new RequestBuilder();
  }

  private static final class RequestBuilder {
    private String requestId = "11111111-1111-1111-1111-111111111111";
    private String actorId = "actor-1";
    private String prompt = "Which report?";
    private List<ClarificationRequest.Question> questions = List.of(question("q1", false));
    private ClarificationRequest.Status status = ClarificationRequest.Status.PENDING;
    private String createdAt = "2026-09-16T10:00:00Z";
    private String expiresAt = "2026-09-17T10:00:00Z";
    private String answeredAt;

    RequestBuilder requestId(String value) {
      requestId = value;
      return this;
    }

    RequestBuilder actorId(String value) {
      actorId = value;
      return this;
    }

    RequestBuilder prompt(String value) {
      prompt = value;
      return this;
    }

    RequestBuilder questions(List<ClarificationRequest.Question> value) {
      questions = value;
      return this;
    }

    RequestBuilder status(ClarificationRequest.Status value) {
      status = value;
      return this;
    }

    RequestBuilder createdAt(String value) {
      createdAt = value;
      return this;
    }

    RequestBuilder expiresAt(String value) {
      expiresAt = value;
      return this;
    }

    RequestBuilder answeredAt(String value) {
      answeredAt = value;
      return this;
    }

    ClarificationRequest build() {
      return new ClarificationRequest(
          requestId,
          actorId,
          "task-1",
          prompt,
          questions,
          Map.of(),
          status,
          createdAt,
          expiresAt,
          answeredAt,
          null,
          List.of());
    }
  }
}
