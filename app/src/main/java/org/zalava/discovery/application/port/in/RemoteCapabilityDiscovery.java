package org.zalava.discovery.application.port.in;

import java.util.List;
import org.zalava.discovery.CapabilityGapClassification;
import org.zalava.discovery.CapabilityGapEvidence.RankedCandidate;

/**
 * Bounded remote capability discovery invoked only after installed lookup found no eligible match.
 * It is recommendation-only: it never downloads, installs, enables or grants authority.
 */
public interface RemoteCapabilityDiscovery {

  Outcome discover(String query, int installedMatchCount);

  record Outcome(CapabilityGapClassification classification, List<RankedCandidate> candidates) {
    public Outcome {
      candidates = List.copyOf(candidates);
    }

    public static Outcome of(CapabilityGapClassification classification) {
      return new Outcome(classification, List.of());
    }
  }

  static RemoteCapabilityDiscovery noop() {
    return (query, installedMatchCount) -> Outcome.of(CapabilityGapClassification.DISABLED);
  }
}
