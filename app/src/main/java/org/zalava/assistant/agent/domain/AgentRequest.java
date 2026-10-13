package org.zalava.assistant.agent.domain;

import java.util.List;

/** Authored question and server-resolved file data are distinct, non-authorizing inputs. */
public record AgentRequest(
    String question, String attachmentContext, List<String> attachmentNames) {
  public AgentRequest {
    question = question == null ? "" : question;
    attachmentContext = attachmentContext == null ? "" : attachmentContext;
    attachmentNames = attachmentNames == null ? List.of() : List.copyOf(attachmentNames);
    if (attachmentContext.length() > 20_000 || attachmentNames.size() > 5)
      throw new IllegalArgumentException("Attachment context exceeds configured bounds");
  }

  public static AgentRequest question(String text) {
    return new AgentRequest(text, "", List.of());
  }
}
