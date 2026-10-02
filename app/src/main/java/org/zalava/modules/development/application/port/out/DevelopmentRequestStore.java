package org.zalava.modules.development.application.port.out;

import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.ModuleDevelopmentRequest;

public interface DevelopmentRequestStore {

  ModuleDevelopmentRequest get(DevelopmentRequestId id);

  ModuleDevelopmentRequest save(ModuleDevelopmentRequest request);
}
