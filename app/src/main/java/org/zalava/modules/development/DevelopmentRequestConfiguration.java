package org.zalava.modules.development;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.development.adapter.out.contract.DevelopmentCandidateEvaluator;
import org.zalava.modules.development.application.AdministratorAuthorizedDevelopmentRequests;
import org.zalava.modules.development.application.DefaultDevelopmentCandidateSubmission;
import org.zalava.modules.development.application.DefaultDevelopmentRequestManagement;
import org.zalava.modules.development.application.DefaultDevelopmentWorkspaceExport;
import org.zalava.modules.development.application.DevelopmentCandidateValidationGateway;
import org.zalava.modules.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.modules.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.modules.development.application.port.in.DevelopmentWorkspaceExport;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;
import org.zalava.modules.development.application.port.out.DevelopmentWorkspacePort;
import org.zalava.web.control.application.AdministratorControlAuthorization;

@Configuration
public class DevelopmentRequestConfiguration {

  @Bean
  DevelopmentRequestManagement rawDevelopmentRequestManagement(
      DevelopmentRequestStore requests,
      @Value("${sea.module-api.version:0.1.0-alpha.3}") String moduleApiVersion) {
    return new DefaultDevelopmentRequestManagement(requests, Clock.systemUTC(), moduleApiVersion);
  }

  @Bean
  @Primary
  DevelopmentRequestManagement developmentRequestManagement(
      @Qualifier("rawDevelopmentRequestManagement") DevelopmentRequestManagement delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedDevelopmentRequests.management(delegate, authorization);
  }

  @Bean
  DevelopmentWorkspaceExport rawDevelopmentWorkspaceExport(
      DevelopmentRequestStore requests, DevelopmentWorkspacePort workspaces) {
    return new DefaultDevelopmentWorkspaceExport(requests, workspaces);
  }

  @Bean
  @Primary
  DevelopmentWorkspaceExport developmentWorkspaceExport(
      @Qualifier("rawDevelopmentWorkspaceExport") DevelopmentWorkspaceExport delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedDevelopmentRequests.workspaces(delegate, authorization);
  }

  @Bean
  DevelopmentCandidateValidationGateway developmentCandidateValidationGateway(
      DevelopmentRequestStore requests) {
    Clock clock = Clock.systemUTC();
    return new DevelopmentCandidateValidationGateway(
        requests, new DevelopmentCandidateEvaluator(clock), clock);
  }

  @Bean
  DevelopmentCandidateSubmission rawDevelopmentCandidateSubmission(
      LocalArtifactInspection artifacts, DevelopmentCandidateValidationGateway gateway) {
    return new DefaultDevelopmentCandidateSubmission(artifacts, gateway);
  }

  @Bean
  @Primary
  DevelopmentCandidateSubmission developmentCandidateSubmission(
      @Qualifier("rawDevelopmentCandidateSubmission") DevelopmentCandidateSubmission delegate,
      AdministratorControlAuthorization authorization) {
    return AdministratorAuthorizedDevelopmentRequests.candidates(delegate, authorization);
  }
}
