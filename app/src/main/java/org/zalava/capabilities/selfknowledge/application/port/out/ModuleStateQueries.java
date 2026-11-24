package org.zalava.capabilities.selfknowledge.application.port.out;

import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;

/** Host lifecycle state, without configuration values or failure details. */
public interface ModuleStateQueries {
  RuntimeState state(String moduleId);
}
