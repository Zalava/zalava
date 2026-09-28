package org.zalava.discovery.application.port.out;

import java.util.List;
import org.zalava.discovery.CapabilityGapEvidence;

/** Persistence boundary for actor-safe no-match and weak-match evidence. */
public interface CapabilityGapEvidenceStore {

  void save(CapabilityGapEvidence evidence);

  List<CapabilityGapEvidence> recent(int limit);
}
