package org.zalava.assistant.agent.application.port.out;

import java.util.List;
import java.util.function.UnaryOperator;

/** Authored input and bounded model context are separate values; neither carries authority. */
public record AgentModelInput(String authoredText, String modelText, List<String> attachmentNames) {
  public static final String ADVISOR_KEY = "zalava.authored-model-input";

  public AgentModelInput(String authoredText, String modelText) {
    this(authoredText, modelText, List.of());
  }

  public AgentModelInput {
    attachmentNames = attachmentNames == null ? List.of() : List.copyOf(attachmentNames);
    authoredText = authoredText == null ? "" : authoredText;
    modelText = modelText == null ? "" : modelText;
  }

  public AgentModelInput mapTexts(UnaryOperator<String> transform) {
    return new AgentModelInput(
        transform.apply(authoredText),
        transform.apply(modelText),
        attachmentNames.stream().map(transform).toList());
  }
}
