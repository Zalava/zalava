package org.zalava.clarification.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.clarification.SeaClarifications;
import org.zalava.clarification.domain.ClarificationDraft;
import org.zalava.clarification.domain.ClarificationRequest;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc component test for the actor-owned clarification surface. It drives the real
 * controller, actor resolver, clarification store and task commands, so ownership, idempotency,
 * expiry, cancellation and job resume are proven over real HTTP.
 */
@AuthenticatedSeaComponentTest
class ClarificationControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SeaClarifications clarifications;
  @Autowired private ActorTaskStore tasks;
  @Autowired private ComponentTestAccounts accounts;

  @Test
  void ownerAnswersTheirClarificationAndThePausedJobResumes() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ANSWERED"))
        .andExpect(jsonPath("$.answers[0].choiceId").value("a"));

    org.assertj.core.api.Assertions.assertThat(tasks.get(actor, reference).getStatus())
        .isEqualTo(Task.Status.todo);

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ANSWERED"));
    org.assertj.core.api.Assertions.assertThat(tasks.get(actor, reference).getStatus())
        .isEqualTo(Task.Status.todo);
  }

  @Test
  void anotherActorCannotAnswerOrReadTheOwnersClarification() throws Exception {
    Account owner = accounts.newActivated(AccountRole.MEMBER);
    Account other = accounts.newActivated(AccountRole.MEMBER);
    Actor ownerActor = new Actor(owner.id());
    ActorTaskReference reference = awaitingTask(ownerActor);
    ClarificationRequest request =
        clarifications.create(ownerActor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(
            get("/api/clarifications/" + request.requestId()).with(accounts.authenticatedAs(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isNotFound());

    org.assertj.core.api.Assertions.assertThat(
            clarifications.get(ownerActor, request.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.PENDING);
    org.assertj.core.api.Assertions.assertThat(tasks.get(ownerActor, reference).getStatus())
        .isEqualTo(Task.Status.awaiting_human_input);
  }

  @Test
  void aConflictingSecondAnswerIsRejectedButAnIdenticalOneIsIdempotent() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("b")))
        .andExpect(status().isConflict());
  }

  @Test
  void anExpiredClarificationRejectsAnAnswerWithGone() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofSeconds(-1));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isGone());
    org.assertj.core.api.Assertions.assertThat(tasks.get(actor, reference).getStatus())
        .isEqualTo(Task.Status.awaiting_human_input);
  }

  @Test
  void cancelledClarificationCannotBeAnsweredAndResumesTheJob() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/cancel")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody("a")))
        .andExpect(status().isConflict());
    org.assertj.core.api.Assertions.assertThat(tasks.get(actor, reference).getStatus())
        .isEqualTo(Task.Status.todo);
  }

  @Test
  void ownerListsPendingClarificationsAndReadsBoundedSafeText() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(get("/api/clarifications").with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].requestId").value(request.requestId()))
        .andExpect(jsonPath("$[0].questions[0].choices[0].choiceId").value("a"));

    mockMvc
        .perform(
            get("/api/clarifications/" + request.requestId() + "/text")
                .with(accounts.authenticatedAs(account)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Which report?")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("[a] Monthly")));
  }

  @Test
  void anEmptyOrMalformedAnswerBodyIsRejectedWithoutChangingState() throws Exception {
    Account account = accounts.newActivated(AccountRole.MEMBER);
    Actor actor = new Actor(account.id());
    ActorTaskReference reference = awaitingTask(actor);
    ClarificationRequest request =
        clarifications.create(actor, reference, draft(), Duration.ofHours(1));

    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[]}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/clarifications/" + request.requestId() + "/answer")
                .with(accounts.authenticatedAs(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"q1\",\"choiceId\":\"zz\"}]}"))
        .andExpect(status().isBadRequest());

    org.assertj.core.api.Assertions.assertThat(
            clarifications.get(actor, request.requestId()).status())
        .isEqualTo(ClarificationRequest.Status.PENDING);
  }

  private ActorTaskReference awaitingTask(Actor actor) {
    ActorTaskReference reference = ActorTaskReference.newReference();
    tasks.save(
        actor,
        reference,
        new Task(
            null, "Clarification job", Instant.now(), Task.Status.awaiting_human_input, "goal"));
    return reference;
  }

  private static ClarificationDraft draft() {
    return new ClarificationDraft(
        "Which report?",
        List.of(
            new ClarificationDraft.Question(
                "q1",
                "Which report?",
                List.of(
                    new ClarificationDraft.Choice("a", "Monthly"),
                    new ClarificationDraft.Choice("b", "Weekly")),
                false)));
  }

  private static String answerBody(String choiceId) {
    return "{\"answers\":[{\"questionId\":\"q1\",\"choiceId\":\"" + choiceId + "\"}]}";
  }
}
