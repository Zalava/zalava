package org.zalava.modules.catalog.application.port.out;

import java.util.Optional;
import org.zalava.api.FactorySecretAccess;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;

/** Configuration and scoped-secret capabilities needed for runtime generation changes. */
public interface ModuleConfigurations {
  java.util.Map<String, ModuleConfigurationSnapshot> activeConfigurations();

  Optional<ModuleConfigurationSnapshot> active(String moduleId);

  Optional<ModuleConfigurationSnapshot> candidate(String moduleId);

  FactorySecretAccess secrets(String moduleId);

  FactorySecretAccess candidateSecrets(String moduleId);

  void promoteCandidate(String moduleId);
}
