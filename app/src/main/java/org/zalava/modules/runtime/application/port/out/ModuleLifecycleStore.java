package org.zalava.modules.runtime.application.port.out;

import java.util.List;
import java.util.Optional;

public interface ModuleLifecycleStore {
  enum DesiredState {
    RUNNING,
    STOPPED
  }

  Optional<DesiredState> desired(String moduleId);

  void set(String moduleId, DesiredState state);

  void registerNewInstall(String moduleId);

  void migrateExisting(List<String> enabledModuleIds);
}
