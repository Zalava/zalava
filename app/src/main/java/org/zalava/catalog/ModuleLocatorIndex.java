package org.zalava.catalog;

import java.net.URI;
import java.util.List;

/**
 * Public catalog which locates module-owned release indexes without duplicating release evidence.
 */
public record ModuleLocatorIndex(
    int schemaVersion, URI indexRepository, String indexPath, List<Module> modules) {

  public ModuleLocatorIndex {
    modules = List.copyOf(modules);
  }

  public record Module(
      String moduleId,
      String displayName,
      String description,
      URI repository,
      String releaseIndexPath) {}
}
