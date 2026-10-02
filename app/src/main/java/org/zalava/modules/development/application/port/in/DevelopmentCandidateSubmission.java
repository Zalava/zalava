package org.zalava.modules.development.application.port.in;

import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.ModuleDevelopmentRequest;

public interface DevelopmentCandidateSubmission {
  ModuleDevelopmentRequest submit(DevelopmentRequestId requestId, String artifactPath);
}
