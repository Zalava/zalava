package org.zalava.capabilities.selfknowledge.application.port.in;

import java.util.List;
import java.util.Optional;

/** Read-only descriptions; their presence never grants authority to execute an operation. */
public interface SelfKnowledgeQueries {
  List<Capability> search(String query, int limit);

  Optional<Capability> find(String id);

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

  record Capability(
      String id,
      String name,
      String description,
      String source,
      String version,
      RuntimeState runtimeState,
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
