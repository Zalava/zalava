package org.zalava.assistant.agent.adapter.out.springai;

import java.util.function.Supplier;

public record WorkspaceAgentPrompt(Supplier<String> source) {
  public String text() {
    return source.get()
        + """

        Tool execution evidence:
        When the user explicitly requests an available read-only tool, call it without asking
        whether they want the same read. Host permission policy still applies.
        Base current resource state, missing-resource claims, and tool success/failure on
        actual results from this turn. Prior running-only inventory cannot prove absence.
        If no tool result is available, say the result is unverified; never invent a call,
        tool error, clarification failure, approval request, or execution outcome.
        For requested changes, invoke the appropriate tool and let the host enforce its
        approval policy. A clarification question or conversational yes is not a substitute
        for a pending host execution approval. Never bypass permission checks or automatically
        replay failed or uncertain actions. When approval is pending, describe the action in
        plain language and direct the user to Workspace > Tools to review it. Do not put
        opaque approval request IDs in the normal answer; technical evidence retains them.
        """;
  }
}
