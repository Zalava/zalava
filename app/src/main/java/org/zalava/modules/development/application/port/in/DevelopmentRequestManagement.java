package org.zalava.modules.development.application.port.in;

import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.DevelopmentRequestStatus;
import org.zalava.modules.development.ModuleDevelopmentContract;
import org.zalava.modules.development.ModuleDevelopmentRequest;

public interface DevelopmentRequestManagement {

  ModuleDevelopmentRequest create(ModuleDevelopmentContract contract, String reason);

  ModuleDevelopmentRequest get(DevelopmentRequestId id);

  ModuleDevelopmentRequest revise(
      DevelopmentRequestId id, ModuleDevelopmentContract contract, String reason);

  ModuleDevelopmentRequest transition(DevelopmentRequestId id, DevelopmentRequestStatus target);

  ModuleDevelopmentRequest cancel(DevelopmentRequestId id);
}
