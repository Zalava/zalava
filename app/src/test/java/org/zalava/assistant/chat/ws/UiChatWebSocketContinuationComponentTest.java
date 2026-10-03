package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationContinuation;
import org.zalava.assistant.conversation.domain.*;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.*;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(
    UiChatWebSocketContinuationComponentTest.ModelConfiguration.class)
class UiChatWebSocketContinuationComponentTest {
  static final Path WORKSPACE = workspace();
  static final AtomicInteger LOGINS = new AtomicInteger();
  @Autowired UiChatWebSocketHandler handler;
  @Autowired AccountLifecycle accounts;
  @Autowired ChannelIdentityLinks links;
  @Autowired ConversationContinuation continuation;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("actorConversations")
  ActorConversations conversations;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> "continuation-component-admin");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void requiresExplicitContinuationAndRechecksOwnershipAndRevocation() throws Exception {
    Account owner =
        accounts.create(
            "continuation-member-" + LOGINS.incrementAndGet(),
            "ComponentPassword-123",
            AccountRole.MEMBER);
    Actor actor = new Actor(owner.id());
    var identity = new ExternalChannelIdentity("telegram", owner.loginName());
    var link = links.link(actor, identity, ChannelOperationScope.of("chat:send"));
    var source =
        continuation.channelConversation(
            actor,
            new ConversationOrigin(
                "telegram", identity.subject(), "private-" + owner.loginName(), true));
    var history = List.of(new ConversationMessage(ConversationMessage.Role.USER, "Source history"));
    conversations.saveAll(actor, source, history);
    var session = session(owner.loginName());
    handler.handleTextMessage(session, command("chat.select", source, ""));
    assertThat(payloads(session)).contains("\"canSend\":false", "\"canContinue\":true", "telegram");
    clearInvocations(session);
    handler.handleTextMessage(
        session, command("chat.send", source, ",\"message\":\"Implicit takeover\""));
    assertThat(payloads(session)).contains("failure");
    assertThat(conversations.findByReference(actor, source)).isEqualTo(history);
    clearInvocations(session);
    handler.handleTextMessage(
        session, command("chat.continue", source, ",\"destination\":\"web\""));
    assertThat(payloads(session))
        .contains("conversation.snapshot", "\"canSend\":true", "Source history");
    assertThat(conversations.findReferences(actor)).hasSize(2);
    assertThat(conversations.findByReference(actor, source)).isEqualTo(history);
    Account other =
        accounts.create(
            "continuation-member-" + LOGINS.incrementAndGet(),
            "ComponentPassword-123",
            AccountRole.MEMBER);
    var foreign = session(other.loginName());
    handler.handleTextMessage(
        foreign, command("chat.continue", source, ",\"destination\":\"web\""));
    assertThat(payloads(foreign)).contains("failure").doesNotContain("Source history");
    assertThat(conversations.findReferences(new Actor(other.id()))).isEmpty();
    links.revoke(actor, link.id());
    clearInvocations(session);
    handler.handleTextMessage(
        session, command("chat.continue", source, ",\"destination\":\"web\""));
    assertThat(payloads(session)).contains("failure").doesNotContain("conversation.snapshot");
    assertThat(conversations.findReferences(actor)).hasSize(2);
  }

  private static TextMessage command(String type, ConversationReference source, String extra) {
    return new TextMessage(
        "{\"protocol\":\"zalava.ui/v1\",\"type\":\""
            + type
            + "\",\"conversationId\":\""
            + source.value()
            + "\""
            + extra
            + "}");
  }

  private static WebSocketSession session(String login) {
    var session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> login);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static String payloads(WebSocketSession session) throws Exception {
    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(sent.capture());
    return sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class ModelConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.ai.chat.model.ChatModel chatModel() {
      return mock(org.springframework.ai.chat.model.ChatModel.class);
    }
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-continuation-component-");
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
