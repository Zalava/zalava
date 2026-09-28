package org.zalava.discovery.application.port.out;

import java.util.List;
import org.zalava.discovery.RemoteCatalogException;
import org.zalava.discovery.RemoteModuleCandidate;

/** Bounded, configured, read-only remote module metadata source. */
public interface RemoteModuleCatalog {

  /** Whether an explicit remote source is configured. A disabled catalog is never queried. */
  boolean configured();

  /**
   * Returns bounded candidates relevant to the normalized query. Implementations must never
   * download, install, enable or execute a module.
   */
  List<RemoteModuleCandidate> lookup(String normalizedQuery) throws RemoteCatalogException;

  static RemoteModuleCatalog disabled() {
    return new RemoteModuleCatalog() {
      @Override
      public boolean configured() {
        return false;
      }

      @Override
      public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
        throw new RemoteCatalogException("Remote module catalog is not configured");
      }
    };
  }
}
