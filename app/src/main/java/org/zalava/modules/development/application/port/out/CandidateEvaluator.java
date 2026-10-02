package org.zalava.modules.development.application.port.out;

import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.ModuleDevelopmentRequest;

public interface CandidateEvaluator {
  CandidateEvaluation evaluate(
      ModuleDevelopmentRequest request, LocalArtifactInspection.InspectedArtifact artifact);
}
