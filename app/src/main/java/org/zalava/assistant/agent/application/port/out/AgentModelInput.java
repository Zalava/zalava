package org.zalava.assistant.agent.application.port.out;

import java.util.function.UnaryOperator;

/** Authored input and bounded model context are separate values; neither carries authority. */
public record AgentModelInput(String authoredText, String modelText) {
  public static final String ADVISOR_KEY = "zalava.authored-model-input";

  public AgentModelInput {
    authoredText = authoredText == null ? "" : authoredText;
    modelText = modelText == null ? "" : modelText;
  }

  public AgentModelInput mapTexts(UnaryOperator<String> transform) {
    return new AgentModelInput(transform.apply(authoredText), transform.apply(modelText));
  }
}
