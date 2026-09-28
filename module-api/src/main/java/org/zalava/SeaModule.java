package org.zalava;

import java.util.List;
import org.zalava.web.SeaWebExtension;

public interface SeaModule {

  ModuleDescriptor descriptor();

  List<ProviderFactory> providerFactories();

  default ModuleConfigurationDescriptor configuration() {
    return ModuleConfigurationDescriptor.none();
  }

  default List<SeaVerificationContributor> verificationContributors() {
    return List.of();
  }

  default List<SeaServiceFactory<?>> serviceFactories() {
    return List.of();
  }

  default List<SeaServiceRequirement> serviceRequirements() {
    return List.of();
  }

  /**
   * Managed OCI services this module declares for SEA to install under administrator-approved
   * resource grants. Added as a default method so modules compiled against earlier {@code
   * module-api} releases remain loadable and simply declare no managed services.
   */
  default List<ManagedServiceDeclaration> managedServices() {
    return List.of();
  }

  default List<SeaWebExtension> webExtensions() {
    return List.of();
  }
}
