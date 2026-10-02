package org.zalava.capabilities.discovery.application.port.in;

import java.util.List;
import org.zalava.capabilities.discovery.CapabilityGapEvidence;

/** Read-only, bounded inspection of persisted capability-gap evidence. */
public interface CapabilityGapEvidenceQueries {

  int MAX_RESULTS = 100;

  List<CapabilityGapEvidence> recent(int limit);
}
