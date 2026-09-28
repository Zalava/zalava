package org.zalava.clarification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.clarification.domain.ClarificationDraft;
import org.zalava.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.junit.jupiter.api.Test;

class ClarificationToolsTest {

  private final Actor owner = new Actor(AccountId.newId());
  private final ActorTaskReference reference = ActorTaskReference.newReference();
  private final ActorExecutionContext actorContext = new ActorExecutionContext();
  private final TaskExecutionContext taskContext = new TaskExecutionContext();
  private final SeaClarifications clarifications =
      new SeaClarifications(new NoOpStore(), Instant::now);
  private final ClarificationTools tools =
      new ClarificationTools(clarifications, actorContext, taskContext);

  @Test
  void anOwnedTaskCanRequestTypedClarificationExactlyOnce() {
    String first =
        actorContext.call(
            owner,
            AccountRole.MEMBER,
            () ->
                taskContext.call(
                    new ActorTaskExecutionReference(owner, reference),
                    () -> tools.request(draft())));
    String second =
        actorContext.call(
            owner,
            AccountRole.MEMBER,
            () ->
                taskContext.call(
                    new ActorTaskExecutionReference(owner, reference),
                    () -> tools.request(draft())));

    assertThat(first).contains("\"status\":\"PENDING\"").contains("Monthly");
    assertThat(second).contains(extractRequestId(first));
    assertThat(clarifications.pendingFor(owner, reference)).hasSize(1);
  }

  @Test
  void aClarificationRequestOutsideAnOwnedTaskIsRejectedWithoutPersisting() {
    String result = tools.request(draft());

    assertThat(result).contains("\"status\":\"REJECTED\"");
    assertThat(clarifications.recent()).isEmpty();
  }

  @Test
  void invalidTypedContentIsRejectedWithoutPersisting() {
    ClarificationDraft invalid =
        new ClarificationDraft(
            "Which?",
            List.of(
                new ClarificationDraft.Question(
                    "q1",
                    "Which?",
                    List.of(new ClarificationDraft.Choice("a", "Only one")),
                    false)));

    String result =
        actorContext.call(
            owner,
            AccountRole.MEMBER,
            () ->
                taskContext.call(
                    new ActorTaskExecutionReference(owner, reference),
                    () -> tools.request(invalid)));

    assertThat(result).contains("\"status\":\"REJECTED\"");
    assertThat(clarifications.recent()).isEmpty();
  }

  private static String extractRequestId(String json) {
    int start = json.indexOf("\"requestId\":\"") + "\"requestId\":\"".length();
    return json.substring(start, json.indexOf('"', start));
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

  private static final class NoOpStore
      implements org.zalava.clarification.application.port.out.ClarificationStore {
    private final List<ClarificationRequest> values = new java.util.ArrayList<>();

    @Override
    public List<ClarificationRequest> load() {
      return List.copyOf(values);
    }

    @Override
    public void save(ClarificationRequest request) {
      values.removeIf(value -> value.requestId().equals(request.requestId()));
      values.add(request);
    }

    @Override
    public void delete(String requestId) {
      values.removeIf(value -> value.requestId().equals(requestId));
    }
  }
}
