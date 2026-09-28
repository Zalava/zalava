package org.zalava.development.application.port.in;

import org.zalava.development.DevelopmentRequestId;
import org.zalava.development.DevelopmentRequestStatus;
import org.zalava.development.ModuleDevelopmentContract;
import org.zalava.development.ModuleDevelopmentRequest;

public interface DevelopmentRequestManagement {

  ModuleDevelopmentRequest create(ModuleDevelopmentContract contract, String reason);

  ModuleDevelopmentRequest get(DevelopmentRequestId id);

  ModuleDevelopmentRequest revise(
      DevelopmentRequestId id, ModuleDevelopmentContract contract, String reason);

  ModuleDevelopmentRequest transition(DevelopmentRequestId id, DevelopmentRequestStatus target);

  ModuleDevelopmentRequest cancel(DevelopmentRequestId id);
}
