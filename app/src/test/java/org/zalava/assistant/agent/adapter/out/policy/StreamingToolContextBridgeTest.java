package org.zalava.assistant.agent.adapter.out.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.capture.ActorTaskCreationContext;
import org.zalava.tasks.domain.ActorTaskReference;

/**
 * Covers the streamed-turn context bridge: tool callbacks executing on other threads (as Spring AI
 * does on reactor threads) must still observe the actor principal — Zalava's policy/scope/audit
 * depends on it — and must record created job references into the caller's capture list.
 */
class StreamingToolContextBridgeTest {

  @Test
  void restoresTheActorPrincipalAroundToolCallbackInvocations() {
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    AtomicReference<String> principalSeenByTool = new AtomicReference<>();
    ToolCallback tool =
        recordingCallback(
            "tool",
            ignored -> {
              var principal = actorExecution.currentPrincipal().orElseThrow();
              principalSeenByTool.set(
                  principal.actor().accountId().value() + ":" + principal.role());
            });
    StreamingToolContextBridge bridge =
        new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext());

    StreamingToolContextBridge.Snapshot snapshot =
        actorExecution.call(actor, AccountRole.MEMBER, bridge::snapshot);
    List<Object> bound = bridge.bind(List.of(tool), snapshot);
    // Simulate a reactor thread: invoke the wrapped callback outside the actor scope.
    ((ToolCallback) bound.getFirst()).call("{}");

    assertThat(principalSeenByTool.get())
        .isEqualTo(actor.accountId().value() + ":" + AccountRole.MEMBER);
    assertThat(actorExecution.currentPrincipal()).isEmpty();
  }

  @Test
  void restoresThePrincipalForMethodToolCallbackProvidersToo() {
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    AtomicReference<String> principalSeenByTool = new AtomicReference<>();
    StreamingToolContextBridge bridge =
        new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext());

    StreamingToolContextBridge.Snapshot snapshot =
        actorExecution.call(actor, AccountRole.ADMIN, bridge::snapshot);
    List<Object> bound =
        bridge.bind(List.of(new AnnotatedTool(principalSeenByTool, actorExecution)), snapshot);
    // @Tool-annotated objects are exposed as providers; resolve and invoke on "another thread".
    assertThat(bound.getFirst()).isInstanceOf(ToolCallbackProvider.class);
    ToolCallback callback = ((ToolCallbackProvider) bound.getFirst()).getToolCallbacks()[0];
    callback.call("{}");

    assertThat(principalSeenByTool.get())
        .isEqualTo(actor.accountId().value() + ":" + AccountRole.ADMIN);
    assertThat(actorExecution.currentPrincipal()).isEmpty();
  }

  @Test
  void recordsCreatedJobReferencesIntoTheCallersCaptureList() {
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    ActorTaskCreationContext taskCreation = new ActorTaskCreationContext();
    Actor actor = new Actor(AccountId.newId());
    ActorTaskReference created = ActorTaskReference.newReference();
    ToolCallback tool = recordingCallback("creator", ignored -> taskCreation.taskCreated(created));
    StreamingToolContextBridge bridge =
        new StreamingToolContextBridge(actorExecution, taskCreation);

    // The chat layer captures around the whole turn; the snapshot must share that capture list.
    ActorTaskCreationContext.Capture<List<Object>> capture =
        taskCreation.capture(
            () -> {
              StreamingToolContextBridge.Snapshot snapshot =
                  actorExecution.call(actor, AccountRole.MEMBER, bridge::snapshot);
              List<Object> bound = bridge.bind(List.of(tool), snapshot);
              ((ToolCallback) bound.getFirst()).call("{}");
              return bound;
            });

    assertThat(capture.taskReferences()).containsExactly(created);
  }

  @Test
  void aSnapshotWithoutAnActivePrincipalLeavesTheToolsUntouched() {
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    ToolCallback tool = recordingCallback("tool", ignored -> {});
    StreamingToolContextBridge bridge =
        new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext());

    StreamingToolContextBridge.Snapshot snapshot = bridge.snapshot();

    assertThat(snapshot).isNull();
    assertThat(bridge.bind(List.of(tool), null)).containsExactly(tool);
  }

  @Test
  void aToolFailureRestoresTheContextsBeforePropagating() {
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    Actor actor = new Actor(AccountId.newId());
    ToolCallback failing =
        recordingCallback(
            "failing",
            ignored -> {
              throw new IllegalStateException("tool exploded");
            });
    StreamingToolContextBridge bridge =
        new StreamingToolContextBridge(actorExecution, new ActorTaskCreationContext());

    StreamingToolContextBridge.Snapshot snapshot =
        actorExecution.call(actor, AccountRole.MEMBER, bridge::snapshot);
    ToolCallback wrapped = (ToolCallback) bridge.bind(List.of(failing), snapshot).getFirst();

    assertThatThrownBy(() -> wrapped.call("{}")).isInstanceOf(IllegalStateException.class);
    assertThat(actorExecution.currentPrincipal()).isEmpty();
  }

  private static ToolCallback recordingCallback(
      String name, java.util.function.Consumer<String> action) {
    return new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder().name(name).description("test").inputSchema("{}").build();
      }

      @Override
      public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
      }

      @Override
      public String call(String toolInput) {
        action.accept(toolInput);
        return "ok";
      }
    };
  }

  /** A {@code @Tool}-annotated object like the ones Zalava passes to {@code .tools(...)}. */
  static final class AnnotatedTool {
    private final AtomicReference<String> seen;
    private final ActorExecutionContext actorExecution;

    AnnotatedTool(AtomicReference<String> seen, ActorExecutionContext actorExecution) {
      this.seen = seen;
      this.actorExecution = actorExecution;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "annotated", description = "test")
    String run(String input) {
      var principal = actorExecution.currentPrincipal().orElseThrow();
      seen.set(principal.actor().accountId().value() + ":" + principal.role());
      return "ok";
    }
  }
}
