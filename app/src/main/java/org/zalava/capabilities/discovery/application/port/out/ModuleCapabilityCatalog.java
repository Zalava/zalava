package org.zalava.capabilities.discovery.application.port.out;

import java.util.List;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions.Module;

/**
 * Stable catalog descriptions; release resolution remains in the explicit installation workflow.
 */
public interface ModuleCapabilityCatalog {
  List<Module> modules();
}
