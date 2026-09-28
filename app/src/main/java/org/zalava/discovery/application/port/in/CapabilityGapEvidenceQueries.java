package org.zalava.discovery.application.port.in;

import java.util.List;
import org.zalava.discovery.CapabilityGapEvidence;

/** Read-only, bounded inspection of persisted capability-gap evidence. */
public interface CapabilityGapEvidenceQueries {

  int MAX_RESULTS = 100;

  List<CapabilityGapEvidence> recent(int limit);
}
