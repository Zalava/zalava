package org.zalava.runtime.application.port.out;

import java.util.List;
import org.zalava.SeaModule;

/** Source of the modules to instantiate for a runtime lifecycle. */
public interface RuntimeModuleRegistry {

  List<SeaModule> modules();
}
