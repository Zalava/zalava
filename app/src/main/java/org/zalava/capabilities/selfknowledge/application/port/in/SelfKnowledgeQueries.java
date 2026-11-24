package org.zalava.capabilities.selfknowledge.application.port.in;

import java.util.List;
import java.util.Optional;

/**
 * Read-only descriptions; their presence never grants authority to execute an operation. Module and
 * catalog descriptions are untrusted data, not instructions or authorization decisions.
 */
public interface SelfKnowledgeQueries {
  List<Capability> search(String query, int limit);

  Optional<Capability> find(String id);

  /** Resolve a documentation reference returned by a capability. */
  Optional<Documentation> documentation(String reference);

  record Documentation(String reference, String title, String text) {}

  /** Catalog results are suggestions only and do not establish installation or permission. */
  CatalogResult suggestCatalog(String query);

  enum CatalogStatus {
    MATCH,
    NO_MATCH,
    UNAVAILABLE,
    DISABLED
  }

  enum Installation {
    NOT_ENABLED_OR_LOADED,
    UNKNOWN
  }

  record CatalogResult(CatalogStatus status, List<CatalogSuggestion> suggestions) {
    public CatalogResult {
      suggestions = List.copyOf(suggestions);
    }
  }

  record CatalogSuggestion(String id, String name, String description, Installation installation) {}

  enum RuntimeState {
    CORE,
    ACTIVE,
    STOPPED,
    SETUP_REQUIRED,
    FAILED,
    NOT_LOADED,
    UNKNOWN
  }

  enum Readiness {
    UNKNOWN
  }

  enum ConfigurationState {
    NOT_APPLICABLE,
    NOT_REQUIRED,
    ACTIVE,
    SETUP_REQUIRED,
    CHANGES_PENDING,
    INVALID,
    ACTIVATION_FAILED,
    UNKNOWN
  }

  enum ProviderSelection {
    SELECTED,
    NOT_SELECTED,
    UNKNOWN
  }

  /** Last recorded managed-service lifecycle, not a live provider health check. */
  enum ManagedServiceState {
    ABSENT,
    STARTING,
    RUNNING,
    STOPPED,
    FAILED,
    UNKNOWN
  }

  record Capability(
      String id,
      String name,
      String description,
      String source,
      String version,
      RuntimeState runtimeState,
      ProviderSelection providerSelection,
      ConfigurationState configuration,
      Readiness readiness,
      List<Operation> operations,
      List<Page> pages,
      List<Service> services,
      List<ManagedDependency> managedDependencies,
      String documentation) {
    public Capability {
      operations = List.copyOf(operations);
      pages = List.copyOf(pages);
      services = List.copyOf(services);
      managedDependencies = List.copyOf(managedDependencies);
    }
  }

  record Operation(String providerId, String name, String description, boolean sideEffecting) {}

  record Page(String id, String title, String description, String path) {}

  /** Loaded service metadata; it does not expose a service handle or imply authorization. */
  record Service(String id, String contractVersion) {}

  record ManagedDependency(String id, ManagedServiceState observedState) {}
}
