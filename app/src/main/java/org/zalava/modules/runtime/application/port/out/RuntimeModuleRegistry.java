package org.zalava.modules.runtime.application.port.out;

import java.util.List;
import org.zalava.api.ZalavaModule;

/** Source of the modules to instantiate for a runtime lifecycle. */
public interface RuntimeModuleRegistry {

  List<ZalavaModule> modules();
}
