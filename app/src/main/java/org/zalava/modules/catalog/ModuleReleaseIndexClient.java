package org.zalava.modules.catalog;

import java.net.URI;

/** Read-only, bounded retrieval of a module-owned release manifest. */
public final class ModuleReleaseIndexClient {
  private final org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval
      retrieval;

  public ModuleReleaseIndexClient() {
    this(new org.zalava.modules.catalog.adapter.out.http.JdkModuleReleaseIndexRetrieval());
  }

  ModuleReleaseIndexClient(
      org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval retrieval) {
    this.retrieval = retrieval;
  }

  public ModuleReleaseIndex load(URI uri, String bearerToken) {
    return retrieval.load(uri, bearerToken);
  }
}
