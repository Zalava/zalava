package org.zalava.catalog.application.port.out;

import java.net.URI;
import org.zalava.catalog.ModuleReleaseIndex;

/** Retrieves an immutable module release manifest from a validated origin. */
public interface ModuleReleaseIndexRetrieval {
  ModuleReleaseIndex load(URI uri, String bearerToken);
}
