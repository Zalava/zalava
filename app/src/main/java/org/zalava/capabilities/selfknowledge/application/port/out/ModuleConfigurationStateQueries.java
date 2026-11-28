package org.zalava.capabilities.selfknowledge.application.port.out;

import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ConfigurationState;

/** Reads only the host's configuration state for a module that declares settings. */
public interface ModuleConfigurationStateQueries {
  ConfigurationState state(String moduleId);
}
