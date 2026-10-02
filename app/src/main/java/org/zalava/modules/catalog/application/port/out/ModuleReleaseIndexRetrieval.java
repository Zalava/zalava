package org.zalava.modules.catalog.application.port.out;

import java.net.URI;
import org.zalava.modules.catalog.ModuleReleaseIndex;

/** Retrieves an immutable module release manifest from a validated origin. */
public interface ModuleReleaseIndexRetrieval {
  ModuleReleaseIndex load(URI uri, String bearerToken);
}
