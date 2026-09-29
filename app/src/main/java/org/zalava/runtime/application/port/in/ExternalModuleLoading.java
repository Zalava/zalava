package org.zalava.runtime.application.port.in;

import java.io.IOException;
import java.util.List;
import org.zalava.ZalavaModule;

/** Lifecycle boundary for loading externally enabled SEA modules. */
public interface ExternalModuleLoading extends AutoCloseable {

  List<ZalavaModule> loadModules();

  @Override
  void close() throws IOException;
}
