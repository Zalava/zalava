package org.zalava.api;

import java.util.List;
import org.zalava.api.extensions.channels.ZalavaChannel;
import org.zalava.api.extensions.managed.ManagedServiceDeclaration;
import org.zalava.api.extensions.web.ZalavaWebExtension;

public interface ZalavaModule {

  ModuleDescriptor descriptor();

  List<ProviderFactory> providerFactories();

  default ModuleConfigurationDescriptor configuration() {
    return ModuleConfigurationDescriptor.none();
  }

  default List<ZalavaVerificationContributor> verificationContributors() {
    return List.of();
  }

  default List<ZalavaServiceFactory<?>> serviceFactories() {
    return List.of();
  }

  default List<ZalavaServiceRequirement> serviceRequirements() {
    return List.of();
  }

  /** Module-owned channel transports; Core owns their registration, identity, and routing. */
  default List<ZalavaChannel> channels() {
    return List.of();
  }

  /**
   * Managed OCI services this module declares for Zalava to install under administrator-approved
   * resource grants. Added as a default method so modules compiled against earlier {@code
   * module-api} releases remain loadable and simply declare no managed services.
   */
  default List<ManagedServiceDeclaration> managedServices() {
    return List.of();
  }

  default List<ZalavaWebExtension> webExtensions() {
    return List.of();
  }
}
