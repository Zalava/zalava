package org.zalava.development.application.port.out;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.ModuleDevelopmentRequest;

public interface DevelopmentRequestStore {

  ModuleDevelopmentRequest get(DevelopmentRequestId id);

  ModuleDevelopmentRequest save(ModuleDevelopmentRequest request);
}
