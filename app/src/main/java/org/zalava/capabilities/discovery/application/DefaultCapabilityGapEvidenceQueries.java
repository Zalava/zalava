package org.zalava.capabilities.discovery.application;

import java.util.List;
import org.zalava.capabilities.discovery.CapabilityGapEvidence;
import org.zalava.capabilities.discovery.application.port.in.CapabilityGapEvidenceQueries;
import org.zalava.capabilities.discovery.application.port.out.CapabilityGapEvidenceStore;

public final class DefaultCapabilityGapEvidenceQueries implements CapabilityGapEvidenceQueries {

  private final CapabilityGapEvidenceStore evidenceStore;

  public DefaultCapabilityGapEvidenceQueries(CapabilityGapEvidenceStore evidenceStore) {
    this.evidenceStore = evidenceStore;
  }

  @Override
  public List<CapabilityGapEvidence> recent(int limit) {
    if (limit < 1 || limit > MAX_RESULTS) {
      throw new IllegalArgumentException(
          "Capability gap evidence limit must be between 1 and " + MAX_RESULTS);
    }
    return evidenceStore.recent(limit);
  }
}
