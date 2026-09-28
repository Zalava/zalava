package org.zalava.managed;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.zalava.control.application.AdministratorControlAuthorization;
import org.zalava.managed.adapter.out.filesystem.FileSystemManagedServiceBackupAdapter;
import org.zalava.managed.adapter.out.filesystem.FileSystemManagedServiceInstallRequestStore;
import org.zalava.managed.adapter.out.filesystem.FileSystemManagedServiceStateStore;
import org.zalava.managed.adapter.out.filesystem.FileSystemManagedServiceUpgradeRequestStore;
import org.zalava.managed.adapter.out.runtime.ModuleManagedServiceEngine;
import org.zalava.managed.application.DefaultDeclaredManagedServices;
import org.zalava.managed.application.DefaultManagedServiceDiagnostics;
import org.zalava.managed.application.DefaultManagedServiceInstallation;
import org.zalava.managed.application.DefaultManagedServiceQueries;
import org.zalava.managed.application.DefaultManagedServiceUpgrade;
import org.zalava.managed.application.DefaultModuleManagedServiceInstallation;
import org.zalava.managed.application.ManagedServiceInstallPlanning;
import org.zalava.managed.application.ManagedServiceReconciler;
import org.zalava.managed.application.ManagedServiceUpgradeRequest;
import org.zalava.managed.application.port.in.DeclaredManagedServices;
import org.zalava.managed.application.port.in.ManagedServiceDiagnostics;
import org.zalava.managed.application.port.in.ManagedServiceInstallation;
import org.zalava.managed.application.port.in.ManagedServiceQueries;
import org.zalava.managed.application.port.in.ManagedServiceUpgrade;
import org.zalava.managed.application.port.in.ModuleManagedServiceInstallation;
import org.zalava.managed.application.port.out.ManagedServiceBackupPort;
import org.zalava.managed.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.ManagedServiceUpgradeRequestStore;
import org.zalava.managed.application.port.out.OciServiceEngine;
import org.zalava.runtime.application.port.in.RuntimeQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

/**
 * Wires the managed-service reconciler to the module-provided engine and exposes the administrator
 * install, upgrade, and diagnostics workflows. The reconciler runs only when these workflows drive
 * it; no background scheduler is registered. Startup recovery runs against the raw upgrade service
 * because it continues an already-administrator-approved operation.
 */
@Configuration
class ManagedServiceConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ManagedServiceConfiguration.class);

  @Bean
  FileSystemManagedServiceStateStore managedServiceStateStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemManagedServiceStateStore(workspace.getFilePath());
  }

  @Bean
  FileSystemManagedServiceInstallRequestStore managedServiceInstallRequestStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemManagedServiceInstallRequestStore(workspace.getFilePath());
  }

  @Bean
  FileSystemManagedServiceUpgradeRequestStore managedServiceUpgradeRequestStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemManagedServiceUpgradeRequestStore(workspace.getFilePath());
  }

  @Bean
  FileSystemManagedServiceBackupAdapter managedServiceBackupAdapter(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemManagedServiceBackupAdapter(workspace.getFilePath());
  }

  @Bean
  ModuleManagedServiceEngine moduleManagedServiceEngine(RuntimeQueries runtime) {
    return new ModuleManagedServiceEngine(runtime);
  }

  @Bean
  ManagedServiceReconciler managedServiceReconciler(
      ManagedServiceStateStore states, OciServiceEngine engine) {
    return new ManagedServiceReconciler(states, engine, Clock.systemUTC());
  }

  @Bean
  ManagedServiceInstallPlanning managedServiceInstallPlanning() {
    return new ManagedServiceInstallPlanning();
  }

  @Bean
  ManagedServiceInstallation rawManagedServiceInstallation(
      ManagedServiceInstallPlanning planning,
      ManagedServiceInstallRequestStore requests,
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler) {
    return new DefaultManagedServiceInstallation(
        planning, requests, states, reconciler, Clock.systemUTC());
  }

  @Bean
  ManagedServiceUpgrade rawManagedServiceUpgrade(
      ManagedServiceUpgradeRequestStore requests,
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler,
      OciServiceEngine engine,
      ManagedServiceBackupPort backups) {
    return new DefaultManagedServiceUpgrade(
        requests, states, reconciler, engine, backups, Clock.systemUTC());
  }

  @Bean
  ManagedServiceDiagnostics rawManagedServiceDiagnostics(
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler,
      OciServiceEngine engine) {
    return new DefaultManagedServiceDiagnostics(states, reconciler, engine, Clock.systemUTC());
  }

  @Bean
  ManagedServiceQueries managedServiceQueries(ManagedServiceStateStore states) {
    return new DefaultManagedServiceQueries(states);
  }

  @Bean
  DeclaredManagedServices declaredManagedServices(RuntimeQueries runtime) {
    return new DefaultDeclaredManagedServices(runtime);
  }

  @Bean
  ModuleManagedServiceInstallation moduleManagedServiceInstallation(
      DeclaredManagedServices declarations, ManagedServiceInstallation installations) {
    return new DefaultModuleManagedServiceInstallation(declarations, installations);
  }

  /** Continuation of approved work; must not require an administrator security context. */
  @Bean
  org.springframework.boot.ApplicationRunner managedServiceUpgradeRecovery(
      @Qualifier("rawManagedServiceUpgrade") ManagedServiceUpgrade rawUpgrades) {
    return args -> {
      try {
        rawUpgrades.recover();
      } catch (RuntimeException ex) {
        // Recovery failure must not prevent startup; the request keeps its persisted phase and an
        // administrator can retry or deny it after inspecting the diagnostics surface.
        log.warn("Managed-service upgrade recovery failed at startup: {}", ex.getMessage());
      }
    };
  }

  @Bean
  @Primary
  ManagedServiceInstallation managedServiceInstallation(
      @Qualifier("rawManagedServiceInstallation") ManagedServiceInstallation delegate,
      AdministratorControlAuthorization authorization) {
    return new ManagedServiceInstallation() {
      @Override
      public org.zalava.managed.application.ManagedServiceInstallRequest plan(
          org.zalava.managed.application.port.in.ManagedServiceInstallation.PlanRequest
              request) {
        return authorization.call("managed-service-install:plan", () -> delegate.plan(request));
      }

      @Override
      public org.zalava.managed.application.ManagedServiceInstallRequest get(String requestId) {
        return authorization.call(
            "managed-service-install:" + requestId, () -> delegate.get(requestId));
      }

      @Override
      public List<org.zalava.managed.application.ManagedServiceInstallRequest> recent(
          int limit) {
        return authorization.call("managed-service-install:recent", () -> delegate.recent(limit));
      }

      @Override
      public org.zalava.managed.application.ManagedServiceInstallRequest allow(
          String requestId) {
        return authorization.call(
            "managed-service-install-allow:" + requestId, () -> delegate.allow(requestId));
      }

      @Override
      public org.zalava.managed.application.ManagedServiceInstallRequest deny(
          String requestId) {
        return authorization.call(
            "managed-service-install-deny:" + requestId, () -> delegate.deny(requestId));
      }
    };
  }

  @Bean
  @Primary
  ManagedServiceUpgrade managedServiceUpgrade(
      @Qualifier("rawManagedServiceUpgrade") ManagedServiceUpgrade delegate,
      AdministratorControlAuthorization authorization) {
    return new ManagedServiceUpgrade() {
      @Override
      public ManagedServiceUpgradeRequest plan(ManagedServiceUpgrade.UpgradePlanRequest request) {
        return authorization.call("managed-service-upgrade:plan", () -> delegate.plan(request));
      }

      @Override
      public ManagedServiceUpgradeRequest get(String requestId) {
        return authorization.call(
            "managed-service-upgrade:" + requestId, () -> delegate.get(requestId));
      }

      @Override
      public List<ManagedServiceUpgradeRequest> recent(int limit) {
        return authorization.call("managed-service-upgrade:recent", () -> delegate.recent(limit));
      }

      @Override
      public ManagedServiceUpgradeRequest allow(String requestId, long expectedGeneration) {
        return authorization.call(
            "managed-service-upgrade-allow:" + requestId,
            () -> delegate.allow(requestId, expectedGeneration));
      }

      @Override
      public ManagedServiceUpgradeRequest deny(String requestId, long expectedGeneration) {
        return authorization.call(
            "managed-service-upgrade-deny:" + requestId,
            () -> delegate.deny(requestId, expectedGeneration));
      }

      @Override
      public void recover() {
        authorization.run("managed-service-upgrade:recover", delegate::recover);
      }
    };
  }

  @Bean
  @Primary
  ManagedServiceDiagnostics managedServiceDiagnostics(
      @Qualifier("rawManagedServiceDiagnostics") ManagedServiceDiagnostics delegate,
      AdministratorControlAuthorization decorator) {
    return new ManagedServiceDiagnostics() {
      @Override
      public ManagedServiceDiagnostics.DiagnosticsReport inspect(String serviceId) {
        return decorator.call(
            "managed-service:inspect:" + serviceId, () -> delegate.inspect(serviceId));
      }

      @Override
      public java.util.List<String> recentLogs(String serviceId, int maximumLines) {
        return decorator.call(
            "managed-service:logs:" + serviceId,
            () -> delegate.recentLogs(serviceId, maximumLines));
      }

      @Override
      public ManagedServiceDiagnostics.DiagnosticsReport restart(String serviceId) {
        return decorator.call(
            "managed-service:restart:" + serviceId, () -> delegate.restart(serviceId));
      }
    };
  }
}
