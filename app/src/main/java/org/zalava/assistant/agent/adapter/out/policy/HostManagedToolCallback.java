package org.zalava.assistant.agent.adapter.out.policy;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Marks a host-owned callback for policy checks without classifying provider callbacks. */
public final class HostManagedToolCallback implements ToolCallback {
  private final ToolCallback delegate;

  public HostManagedToolCallback(ToolCallback delegate) {
    this.delegate = delegate;
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
  public String call(String input) {
    return delegate.call(input);
  }

  @Override
  public String call(String input, ToolContext context) {
    return delegate.call(input, context);
  }
}
