package org.zalava.agent.adapter.out.policy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tools.ActorTaskCreationContext;

/**
 * Propagates SEA's ThreadLocal request contexts into streamed model turns.
 *
 * <p>During a streamed turn, Spring AI executes tool callbacks on reactor threads. {@code
 * ActorExecutionContext} (actor identity → provider policy/scope/audit) and {@code
 * ActorTaskCreationContext} ("View job" capture) are ThreadLocal-based, so tool callbacks would
 * otherwise run without the authenticated actor: provider tools would downgrade to the anonymous
 * {@code agent} identity and created jobs would be lost. The bridge snapshots both contexts on the
 * calling thread before the stream starts and restores them around each tool invocation, whatever
 * thread it runs on.
 */
public final class StreamingToolContextBridge {

  private final ActorExecutionContext actorExecution;
  private final ActorTaskCreationContext taskCreation;

  public StreamingToolContextBridge(
      ActorExecutionContext actorExecution, ActorTaskCreationContext taskCreation) {
    this.actorExecution = actorExecution;
    this.taskCreation = taskCreation;
  }

  /**
   * Snapshot taken on the thread that starts the turn. {@code null} when no actor principal is
   * active (legacy/system callers) — binding is then a no-op and tools run exactly as before.
   */
  public Snapshot snapshot() {
    ActorExecutionContext.Principal principal = actorExecution.currentPrincipal().orElse(null);
    if (principal == null) {
      return null;
    }
    // Reuse the caller's active capture list when present: tool invocations on reactor threads
    // append to this shared list, and the chat layer's Capture (taken on the calling thread around
    // the whole turn) reads the same instance after the stream completes.
    List<ActorTaskReference> references = taskCreation.currentReferences();
    if (references == null) {
      references = new ArrayList<>();
    }
    return new Snapshot(principal, references);
  }

  /**
   * Binds the snapshot onto every tool object: {@link ToolCallback} instances are wrapped so each
   * call restores the snapshot; {@code @Tool}-annotated objects are converted to method callbacks
   * wrapped identically.
   */
  public List<Object> bind(List<Object> tools, Snapshot snapshot) {
    if (snapshot == null) {
      return tools;
    }
    List<Object> bound = new ArrayList<>(tools.size());
    for (Object tool : tools) {
      bound.add(wrap(tool, snapshot));
    }
    return bound;
  }

  private Object wrap(Object tool, Snapshot snapshot) {
    if (tool instanceof ToolCallback callback) {
      return new ContextRestoringToolCallback(callback, this, snapshot);
    }
    return new ContextRestoringToolCallbackProvider(tool, this, snapshot);
  }

  private <T> T withinContext(Snapshot snapshot, java.util.function.Supplier<T> operation) {
    List<ActorTaskReference> previous = taskCreation.currentReferences();
    taskCreation.installReferences(snapshot.taskReferences());
    try {
      ActorExecutionContext.Principal principal = snapshot.principal();
      return actorExecution.call(principal.actor(), principal.role(), operation::get);
    } finally {
      taskCreation.installReferences(previous);
    }
  }

  /** Immutable snapshot of the ThreadLocal request contexts taken when the turn starts. */
  public static final class Snapshot {
    private final ActorExecutionContext.Principal principal;
    private final List<ActorTaskReference> taskReferences;

    private Snapshot(
        ActorExecutionContext.Principal principal, List<ActorTaskReference> taskReferences) {
      this.principal = principal;
      this.taskReferences = taskReferences;
    }

    public ActorExecutionContext.Principal principal() {
      return principal;
    }

    public List<ActorTaskReference> taskReferences() {
      return taskReferences;
    }
  }

  private static final class ContextRestoringToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final StreamingToolContextBridge bridge;
    private final Snapshot snapshot;

    private ContextRestoringToolCallback(
        ToolCallback delegate, StreamingToolContextBridge bridge, Snapshot snapshot) {
      this.delegate = delegate;
      this.bridge = bridge;
      this.snapshot = snapshot;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
      return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
      return bridge.withinContext(snapshot, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
      return bridge.withinContext(snapshot, () -> delegate.call(toolInput, toolContext));
    }
  }

  /** Adapts a {@code @Tool}-annotated object into context-restoring callbacks. */
  private static final class ContextRestoringToolCallbackProvider
      implements org.springframework.ai.tool.ToolCallbackProvider {
    private final Object toolObject;
    private final StreamingToolContextBridge bridge;
    private final Snapshot snapshot;
    private volatile MethodToolCallbackProvider provider;

    private ContextRestoringToolCallbackProvider(
        Object toolObject, StreamingToolContextBridge bridge, Snapshot snapshot) {
      this.toolObject = toolObject;
      this.bridge = bridge;
      this.snapshot = snapshot;
    }

    private MethodToolCallbackProvider provider() {
      MethodToolCallbackProvider result = provider;
      if (result == null) {
        synchronized (this) {
          result = provider;
          if (result == null) {
            result = MethodToolCallbackProvider.builder().toolObjects(toolObject).build();
            provider = result;
          }
        }
      }
      return result;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
      ToolCallback[] callbacks = provider().getToolCallbacks();
      ToolCallback[] wrapped = new ToolCallback[callbacks.length];
      for (int i = 0; i < callbacks.length; i++) {
        wrapped[i] = new ContextRestoringToolCallback(callbacks[i], bridge, snapshot);
      }
      return wrapped;
    }
  }
}
