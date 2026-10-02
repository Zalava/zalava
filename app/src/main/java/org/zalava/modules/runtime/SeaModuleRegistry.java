package org.zalava.modules.runtime;

import java.util.List;
import org.zalava.ZalavaModule;
import org.zalava.modules.runtime.application.port.out.RuntimeModuleRegistry;

/** Compatibility facade for the runtime-owned module-registry port. */
public interface SeaModuleRegistry extends RuntimeModuleRegistry {

  List<ZalavaModule> modules();
}
