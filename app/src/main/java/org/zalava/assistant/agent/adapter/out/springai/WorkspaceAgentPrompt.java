package org.zalava.assistant.agent.adapter.out.springai;

import java.util.function.Supplier;

public record WorkspaceAgentPrompt(Supplier<String> source) {
  public String text() {
    return source.get();
  }
}
