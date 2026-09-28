package org.zalava.chat.ws;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.zalava.accounts.domain.Actor;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.chat.application.UiExecutionStateQueries;
import org.zalava.chat.application.port.in.ActorChatCommands;
import org.zalava.chat.application.port.in.ActorChatQueries;
import org.zalava.chat.application.port.in.ActorChatStreamListener;
import org.zalava.chat.attachment.application.ChatAttachments;
import org.zalava.chat.attachment.domain.ChatAttachmentIntent;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.ui.protocol.UiCommand;
import org.zalava.ui.protocol.UiCommandDecoder;
import org.zalava.ui.protocol.UiEvent;
import org.zalava.ui.protocol.UiEventJson;
import tools.jackson.databind.ObjectMapper;

/** Authenticated JSON WebSocket adapter consumed by the packaged interactive SEA frontend. */
@Component
@ConditionalOnProperty(
    name = "sea.chat.transport",
    havingValue = "spring-websocket",
    matchIfMissing = true)
public final class UiChatWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(UiChatWebSocketHandler.class);

  private final ObjectMapper objectMapper;
  private final AuthenticatedActorResolver actors;
  private final ActorChatCommands commands;
  private final ActorChatQueries queries;
  private final UiExecutionStateQueries executionStates;
  private final UiApprovalDecisions approvalDecisions;
  private final ChatAttachments attachments;

  public UiChatWebSocketHandler(
      ObjectMapper objectMapper,
      AuthenticatedActorResolver actors,
      ActorChatCommands commands,
      ActorChatQueries queries) {
    this(objectMapper, actors, commands, queries, null, null, null);
  }

  UiChatWebSocketHandler(
      ObjectMapper objectMapper,
      AuthenticatedActorResolver actors,
      ActorChatCommands commands,
      ActorChatQueries queries,
      UiExecutionStateQueries executionStates,
      UiApprovalDecisions approvalDecisions) {
    this(objectMapper, actors, commands, queries, executionStates, approvalDecisions, null);
  }

  @Autowired
  public UiChatWebSocketHandler(
      ObjectMapper objectMapper,
      AuthenticatedActorResolver actors,
      ActorChatCommands commands,
      ActorChatQueries queries,
      UiExecutionStateQueries executionStates,
      UiApprovalDecisions approvalDecisions,
      ChatAttachments attachments) {
    this.objectMapper = objectMapper;
    this.actors = actors;
    this.commands = commands;
    this.queries = queries;
    this.executionStates = executionStates;
    this.approvalDecisions = approvalDecisions;
    this.attachments = attachments;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Actor actor = actor(session);
    sendConversationList(session, actor);
    sendSnapshot(session, selectedConversation(actor));
    if (executionStates != null) {
      executionStates.states(actor).forEach(state -> publishExecutionState(session, state));
    }
    if (attachments != null) {
      attachments.list(actor).forEach(reference -> send(session, attachmentAvailable(reference)));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
    UiCommandDecoder.decode(payload)
        .ifPresentOrElse(
            command -> dispatch(session, command),
            () -> sendFailure(session, "command", "Unsupported or invalid UI command"));
  }

  private void dispatch(WebSocketSession session, UiCommand command) {
    try {
      Actor actor = actor(session);
      switch (command) {
        case UiCommand.CreateConversation ignored -> {
          sendSnapshot(session, commands.createWebConversation(actor));
          sendConversationList(session, actor);
        }
        case UiCommand.SelectConversation select ->
            sendSnapshot(session, new ConversationReference(select.conversationId()));
        case UiCommand.SendChat send -> streamChat(session, actor, send);
        case UiCommand.DecideApproval decide -> decideApproval(session, decide);
        case UiCommand.PutAttachment put -> putAttachment(session, actor, put);
        case UiCommand.DeleteAttachment delete -> deleteAttachment(session, actor, delete);
      }
    } catch (RuntimeException exception) {
      log.warn("UI chat command failed", exception);
      sendFailure(session, "command", "SEA could not complete that request");
    }
  }

  private void decideApproval(WebSocketSession session, UiCommand.DecideApproval command) {
    if (approvalDecisions == null || session.getPrincipal() == null) {
      throw new IllegalStateException("UI approval decisions are unavailable");
    }
    Task task = approvalDecisions.decide(session.getPrincipal().getName(), command);
    send(
        session,
        new UiEvent.JobUpdated(
            UiCommandDecoder.VERSION, command.jobId(), task.getStatus().name(), task.getName()));
  }

  private void putAttachment(
      WebSocketSession session, Actor actor, UiCommand.PutAttachment command) {
    ChatAttachments available = requireAttachments();
    ChatAttachmentIntent intent =
        switch (command.intent()) {
          case "task-only" -> ChatAttachmentIntent.TASK_ONLY;
          case "knowledge-import" -> ChatAttachmentIntent.KNOWLEDGE_IMPORT;
          default -> throw new IllegalArgumentException("Unsupported attachment intent");
        };
    byte[] content = Base64.getDecoder().decode(command.contentBase64());
    send(
        session,
        attachmentAvailable(
            available.put(actor, intent, command.name(), command.contentType(), content)));
  }

  private void deleteAttachment(
      WebSocketSession session, Actor actor, UiCommand.DeleteAttachment command) {
    requireAttachments().delete(actor, command.attachmentId());
    send(session, new UiEvent.AttachmentRemoved(UiCommandDecoder.VERSION, command.attachmentId()));
  }

  private ChatAttachments requireAttachments() {
    if (attachments == null) {
      throw new IllegalStateException("UI attachments are unavailable");
    }
    return attachments;
  }

  private void streamChat(WebSocketSession session, Actor actor, UiCommand.SendChat command) {
    ConversationReference conversation = new ConversationReference(command.conversationId());
    StringBuilder text = new StringBuilder();
    String message = withAttachmentManifest(actor, command);
    commands.streamChat(
        actor,
        conversation,
        message,
        new ActorChatStreamListener() {
          @Override
          public void onDelta(String delta) {
            text.append(delta);
            send(
                session,
                new UiEvent.ChatDelta(UiCommandDecoder.VERSION, conversation.value(), delta));
          }

          @Override
          public void onComplete(String fullText, List<ActorTaskReference> jobs) {
            send(
                session,
                new UiEvent.ChatCompleted(
                    UiCommandDecoder.VERSION,
                    conversation.value(),
                    fullText,
                    jobs.stream().map(job -> job.value()).toList()));
            if (executionStates != null) {
              jobs.forEach(
                  job -> publishExecutionState(session, executionStates.state(actor, job)));
            }
          }

          @Override
          public void onError(RuntimeException failure) {
            log.warn("UI chat stream failed", failure);
            sendFailure(session, "chat.send", "SEA could not complete that request");
          }
        });
  }

  private String withAttachmentManifest(Actor actor, UiCommand.SendChat command) {
    if (command.attachmentIds().isEmpty()) {
      return command.text();
    }
    ChatAttachments available = requireAttachments();
    StringBuilder message = new StringBuilder(command.text());
    for (String attachmentId : command.attachmentIds()) {
      ChatAttachments.Reference reference = available.reference(actor, attachmentId);
      message
          .append("\n\nAttached file: ")
          .append(reference.name())
          .append(" (")
          .append(reference.contentType())
          .append(", ")
          .append(reference.byteCount())
          .append(" bytes, sha256 ")
          .append(reference.sha256(), 0, 12)
          .append(")");
    }
    return message.toString();
  }

  private static UiEvent.AttachmentAvailable attachmentAvailable(
      ChatAttachments.Reference reference) {
    return new UiEvent.AttachmentAvailable(
        UiCommandDecoder.VERSION,
        reference.attachmentId(),
        reference.sourceId(),
        reference.intent() == ChatAttachmentIntent.KNOWLEDGE_IMPORT
            ? "knowledge-import"
            : "task-only",
        reference.name(),
        reference.contentType(),
        reference.byteCount(),
        reference.sha256());
  }

  private void publishExecutionState(
      WebSocketSession session, UiExecutionStateQueries.ExecutionState state) {
    send(
        session,
        new UiEvent.JobUpdated(
            UiCommandDecoder.VERSION, state.jobId(), state.status(), state.summary()));
    state
        .pendingApprovals()
        .forEach(
            approval ->
                send(
                    session,
                    new UiEvent.PermissionRequested(
                        UiCommandDecoder.VERSION,
                        state.jobId(),
                        approval.requestId(),
                        approval.summary())));
  }

  private ConversationReference selectedConversation(Actor actor) {
    List<ConversationReference> conversations = queries.conversations(actor);
    return conversations.isEmpty()
        ? commands.createWebConversation(actor)
        : conversations.getFirst();
  }

  private void sendConversationList(WebSocketSession session, Actor actor) {
    send(
        session,
        new UiEvent.ConversationList(
            UiCommandDecoder.VERSION,
            queries.conversations(actor).stream().map(ConversationReference::value).toList()));
  }

  private void sendSnapshot(WebSocketSession session, ConversationReference conversation) {
    Actor actor = actor(session);
    List<UiEvent.ConversationMessage> messages =
        queries.history(actor, conversation).stream()
            .filter(message -> message.role() != ChatMessage.Role.SYSTEM)
            .map(
                message ->
                    new UiEvent.ConversationMessage(
                        message.role().name().toLowerCase(), message.text()))
            .toList();
    send(
        session,
        new UiEvent.ConversationSnapshot(UiCommandDecoder.VERSION, conversation.value(), messages));
  }

  private Actor actor(WebSocketSession session) {
    if (session.getPrincipal() == null) {
      throw new AccessDeniedException("An authenticated account is required");
    }
    return actors.actorForLogin(session.getPrincipal().getName());
  }

  private void sendFailure(WebSocketSession session, String operation, String message) {
    send(session, new UiEvent.Failure(UiCommandDecoder.VERSION, operation, message));
  }

  private void send(WebSocketSession session, UiEvent event) {
    try {
      if (session.isOpen()) {
        synchronized (session) {
          session.sendMessage(
              new TextMessage(objectMapper.writeValueAsString(UiEventJson.from(event))));
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to emit UI event", exception);
    }
  }
}
