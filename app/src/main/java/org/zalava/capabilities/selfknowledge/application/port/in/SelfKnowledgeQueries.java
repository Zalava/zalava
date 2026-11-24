package org.zalava.capabilities.selfknowledge.application.port.in;

import java.util.List;
import java.util.Optional;

/** Read-only descriptions; their presence never grants authority to execute an operation. */
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

  enum ProviderSelection {
    SELECTED,
    NOT_SELECTED,
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
      Readiness readiness,
      List<Operation> operations,
      List<Page> pages,
      String documentation) {
    public Capability {
      operations = List.copyOf(operations);
      pages = List.copyOf(pages);
    }
  }

  record Operation(String providerId, String name, String description, boolean sideEffecting) {}

  record Page(String id, String title, String description, String path) {}
}
