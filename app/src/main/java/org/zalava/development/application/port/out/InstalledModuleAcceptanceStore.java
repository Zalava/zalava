package org.zalava.development.application.port.out;

import java.util.Optional;
import org.zalava.development.InstalledModuleAcceptance;

/** Persistence boundary for the acceptance evidence that protects module updates. */
public interface InstalledModuleAcceptanceStore {
  Optional<InstalledModuleAcceptance> find(String moduleId);

  InstalledModuleAcceptance save(InstalledModuleAcceptance acceptance);
}
