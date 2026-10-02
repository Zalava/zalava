package org.zalava.modules.development.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.DevelopmentRequestStatus;
import org.zalava.modules.development.ModuleDevelopmentContract;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;

public final class DefaultDevelopmentRequestManagement implements DevelopmentRequestManagement {

  private final DevelopmentRequestStore requests;
  private final Clock clock;
  private final String moduleApiVersion;

  public DefaultDevelopmentRequestManagement(DevelopmentRequestStore requests, Clock clock) {
    this(requests, clock, null);
  }

  public DefaultDevelopmentRequestManagement(
      DevelopmentRequestStore requests, Clock clock, String moduleApiVersion) {
    this.requests = Objects.requireNonNull(requests, "requests must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.moduleApiVersion = moduleApiVersion;
    if (moduleApiVersion != null)
      ModuleDevelopmentContract.requireConcreteApiVersion(moduleApiVersion);
  }

  @Override
  public synchronized ModuleDevelopmentRequest create(
      ModuleDevelopmentContract contract, String reason) {
    contract = seaOwnedContract(contract);
    ModuleDevelopmentContract.requireConcreteModuleVersion(contract);
    return requests.save(
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId(UUID.randomUUID().toString()),
            clock.instant(),
            DevelopmentRequestStatus.PREPARED,
            java.util.List.of(
                new ModuleDevelopmentRequest.Revision(1, clock.instant(), reason, contract))));
  }

  @Override
  public synchronized ModuleDevelopmentRequest get(DevelopmentRequestId id) {
    return requests.get(id);
  }

  @Override
  public synchronized ModuleDevelopmentRequest revise(
      DevelopmentRequestId id, ModuleDevelopmentContract contract, String reason) {
    contract = seaOwnedContract(contract);
    ModuleDevelopmentContract.requireConcreteModuleVersion(contract);
    return requests.save(requests.get(id).revise(contract, reason, clock.instant()));
  }

  @Override
  public synchronized ModuleDevelopmentRequest transition(
      DevelopmentRequestId id, DevelopmentRequestStatus target) {
    return requests.save(requests.get(id).transitionTo(target));
  }

  @Override
  public synchronized ModuleDevelopmentRequest cancel(DevelopmentRequestId id) {
    return transition(id, DevelopmentRequestStatus.CANCELLED);
  }

  private ModuleDevelopmentContract seaOwnedContract(ModuleDevelopmentContract contract) {
    if (moduleApiVersion == null) return contract;
    return new ModuleDevelopmentContract(
        contract.module(),
        contract.purpose(),
        moduleApiVersion,
        contract.tools(),
        contract.expectedErrors(),
        contract.acceptanceScenarios(),
        contract.operationalRequirements(),
        contract.deliveryRequirements());
  }
}
