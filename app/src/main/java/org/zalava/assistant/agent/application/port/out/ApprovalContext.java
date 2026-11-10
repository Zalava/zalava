package org.zalava.assistant.agent.application.port.out;

/** Recorded approval state for the currently authenticated conversation. */
@FunctionalInterface
public interface ApprovalContext {
  String render();
}
