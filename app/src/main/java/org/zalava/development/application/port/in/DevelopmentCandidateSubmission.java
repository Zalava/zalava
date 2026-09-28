package org.zalava.development.application.port.in;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.ModuleDevelopmentRequest;

public interface DevelopmentCandidateSubmission {
  ModuleDevelopmentRequest submit(DevelopmentRequestId requestId, String artifactPath);
}
