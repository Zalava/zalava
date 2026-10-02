package org.zalava.tasks.adapter.out.channel;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zalava.assistant.chat.ws.ActorWebSocketSessions;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.Task;

class ActorWebSocketTaskNotifierTest {
  @Test
  void deliversTaskResultsOnlyToTheSuppliedOwner() throws Exception {
    ActorWebSocketSessions sessions = Mockito.mock(ActorWebSocketSessions.class);
    Actor owner = new Actor(AccountId.newId());

    new ActorWebSocketTaskNotifier(sessions)
        .notify(owner, "private task", Task.Status.completed, "done");

    verify(sessions).send(Mockito.eq(owner), Mockito.contains("private task"));
  }
}
