package org.zalava.assistant.agent.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkspaceAgentPromptTest {
  @Test
  void customizedInstructionsRetainToolEvidenceAndApprovalBoundaries() {
    String prompt = new WorkspaceAgentPrompt(() -> "Custom household instructions.").text();
    assertThat(prompt)
        .startsWith("Custom household instructions.")
        .contains(
            "actual results from this turn",
            "say the result is unverified",
            "without asking",
            "Host permission policy still applies",
            "never invent a call",
            "Do not put",
            "Workspace > Tools",
            "Never bypass");
  }
}
