package org.zalava.catalog;

import java.net.URI;

/** Read-only, bounded retrieval of a module-owned release manifest. */
public final class ModuleReleaseIndexClient implements ModuleReleaseIndexReader {
  private final org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval retrieval;

  public ModuleReleaseIndexClient() {
    this(new org.zalava.catalog.adapter.out.http.JdkModuleReleaseIndexRetrieval());
  }

  ModuleReleaseIndexClient(
      org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval retrieval) {
    this.retrieval = retrieval;
  }

  public ModuleReleaseIndex load(URI uri, String bearerToken) {
    return retrieval.load(uri, bearerToken);
  }
}
