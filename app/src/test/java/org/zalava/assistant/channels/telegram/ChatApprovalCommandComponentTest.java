package org.zalava.assistant.channels.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.assistant.agent.Agent;
import org.zalava.assistant.channels.ChannelRegistry;
import org.zalava.assistant.channels.approval.ChannelApprovalCommands;
import org.zalava.assistant.chat.ChatChannel;
import org.zalava.assistant.chat.ChatTurnResult;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.adapter.out.approval.ZalavaToolApprovalAdapter;
import org.zalava.capabilities.operation.application.DefaultProviderToolOperations;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.capture.TaskCreationContext;
import tools.jackson.databind.JsonNode;

@SuppressWarnings("deprecation")
class ChatApprovalCommandComponentTest {

  @Test
  void telegramChatSupportsApprovalCommandsWithLastShortcut() {
    Harness harness = new Harness();

    assertApprovalCommandsWork(harness.telegramDriver());
  }

  @Test
  void webChatSupportsApprovalCommandsWithLastShortcut() {
    Harness harness = new Harness();

    assertApprovalCommandsWork(harness.webDriver());
  }

  private static void assertApprovalCommandsWork(ChatDriver chat) {
    assertThat(chat.send("add rice")).contains("Approval pending");
    assertThat(chat.providerCalls()).isEmpty();

    assertThat(chat.send("/zalava approve last"))
        .contains("Approval granted once")
        .contains("test-shopping/add_item")
        .contains("The approved tool has run.");
    assertThat(chat.providerCalls()).containsExactly("rice");

    assertThat(chat.send("add salt")).contains("Approval pending");
    assertThat(chat.send("/zalava deny last"))
        .contains("Approval denied")
        .contains("The tool was not run.");
    assertThat(chat.providerCalls()).containsExactly("rice");

    assertThat(chat.send("add pasta")).contains("Approval pending");
    assertThat(chat.send("/zalava always-allow-tool last"))
        .contains("Tool approval policy saved")
        .contains("The approved tool has run.");
    assertThat(chat.providerCalls()).containsExactly("rice", "pasta");

    assertThat(chat.send("add beans")).isEqualTo("Added beans.");
    assertThat(chat.providerCalls()).containsExactly("rice", "pasta", "beans");
  }

  private interface ChatDriver {
    String send(String message);

    List<String> providerCalls();
  }

  private static final class Harness {

    private static final long TELEGRAM_CHAT_ID = 42L;

    private final RecordingProvider provider = new RecordingProvider();
    private final ZalavaToolApprovalRequests approvals = new ZalavaToolApprovalRequests();
    private final ProviderToolOperations operations =
        new DefaultProviderToolOperations(
            providerId ->
                "test-shopping".equals(providerId) ? Optional.of(provider) : Optional.empty(),
            new ZalavaToolApprovalAdapter(approvals),
            List.of(),
            new org.zalava.capabilities.operation.adapter.out.json.JacksonToolArgumentDecoder());
    private final ChannelApprovalCommands approvalCommands =
        new ChannelApprovalCommands(
            approvals,
            operations,
            mock(org.zalava.tasks.application.port.in.TaskCommands.class),
            mock(org.zalava.tasks.application.port.in.TaskQueries.class));
    private final TestAgent agent = new TestAgent(operations);

    private ChatDriver telegramDriver() {
      TelegramClient telegramClient = mock(TelegramClient.class);
      TelegramChannel telegram =
          new TelegramChannel(
              "token",
              "allowed_user",
              telegramClient,
              agent,
              new ChannelRegistry(),
              new org.zalava.assistant.voice.adapter.out.DisabledVoiceTranscription(),
              new org.zalava.assistant.voice.VoiceProperties(),
              approvalCommands);
      return new ChatDriver() {
        @Override
        public String send(String message) {
          telegram.consume(updateFrom("allowed_user", message, TELEGRAM_CHAT_ID));
          return sentMessages(telegramClient).getLast().getText();
        }

        @Override
        public List<String> providerCalls() {
          return provider.calls();
        }
      };
    }

    private ChatDriver webDriver() {
      ChatChannel web =
          new ChatChannel(
              agent,
              new ChannelRegistry(),
              mock(ChatMemoryRepository.class),
              new TaskCreationContext(),
              approvalCommands);
      return new ChatDriver() {
        @Override
        public String send(String message) {
          ChatTurnResult result = web.chat("web", message);
          return result.text();
        }

        @Override
        public List<String> providerCalls() {
          return provider.calls();
        }
      };
    }
  }

  private static final class TestAgent implements Agent {

    private final ProviderToolOperations operations;

    private TestAgent(ProviderToolOperations operations) {
      this.operations = operations;
    }

    @Override
    public String respondTo(String conversationId, String question) {
      if (!question.startsWith("add ")) {
        return "Unsupported test message.";
      }
      String item = question.substring("add ".length());
      ProviderToolOperations.ToolInvocationOutcome outcome =
          operations.invoke(
              ProviderToolOperations.ToolInvocationCommand.operator(
                  "test-shopping",
                  "add_item",
                  "{\"name\":\"" + item + "\"}",
                  new InvocationContext("agent", false, Map.of("source", "component-chat"))));
      return switch (outcome.status()) {
        case EXECUTED -> "Added " + item + ".";
        case PENDING_APPROVAL -> "Approval pending: " + outcome.approval().requestId();
        case DENIED -> "Denied " + item + ".";
      };
    }

    @Override
    public <T> T prompt(String conversationId, String input, Class<T> result) {
      throw new UnsupportedOperationException("prompt is not used by this component test");
    }
  }

  private static final class RecordingProvider implements ZalavaProvider {

    private final List<String> calls = new ArrayList<>();

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "test-shopping",
          "test-module",
          "test",
          "Test Shopping",
          "Test shopping provider.",
          "1.0.0",
          ProviderCapabilities.toolsOnly(),
          List.of("shopping-list"),
          Map.of("list", "test"));
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(
          new ZalavaToolDescriptor("add_item", "Adds an item.", true, List.of("shopping-list")));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, java.util.Map<String, Object> argumentValues, InvocationContext context) {
      JsonNode arguments = new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
      calls.add(arguments.get("name").stringValue(""));
      return ZalavaOperationResult.success(Map.of("name", arguments.get("name").stringValue("")));
    }

    private List<String> calls() {
      return List.copyOf(calls);
    }
  }

  private static Update updateFrom(String username, String text, long chatId) {
    Update update = mock(Update.class);
    Message message = mock(Message.class);
    User user = mock(User.class);

    when(update.hasMessage()).thenReturn(true);
    when(update.getMessage()).thenReturn(message);
    when(message.hasText()).thenReturn(true);
    when(message.getText()).thenReturn(text);
    when(message.getChatId()).thenReturn(chatId);
    when(message.getFrom()).thenReturn(user);
    when(user.getUserName()).thenReturn(username);

    return update;
  }

  private static List<SendMessage> sentMessages(TelegramClient telegramClient) {
    return mockingDetails(telegramClient).getInvocations().stream()
        .map(invocation -> invocation.getArguments()[0])
        .filter(SendMessage.class::isInstance)
        .map(SendMessage.class::cast)
        .toList();
  }
}
