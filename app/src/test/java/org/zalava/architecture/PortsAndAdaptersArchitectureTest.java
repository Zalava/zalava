package org.zalava.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.zalava.architecture.fixture.application.FileSystemCoupledApplicationFixture;
import org.zalava.architecture.fixture.domain.AdapterCoupledDomainFixture;
import org.zalava.architecture.fixture.domain.SpringCoupledDomainFixture;

@AnalyzeClasses(packages = "org.zalava", importOptions = ImportOption.DoNotIncludeTests.class)
class PortsAndAdaptersArchitectureTest {
  @Test
  void hostPackagesUseTheReviewedOwnershipGroups() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("org.zalava");
    assertThat(classes.stream().map(type -> type.getPackageName()))
        .allMatch(
            name ->
                name.equals("org.zalava")
                    || java.util.stream.Stream.of(
                            "identity",
                            "assistant",
                            "capabilities",
                            "tasks",
                            "knowledge",
                            "modules",
                            "platform",
                            "web",
                            "api")
                        .anyMatch(
                            group ->
                                name.equals("org.zalava." + group)
                                    || name.startsWith("org.zalava." + group + ".")));
  }

  @ArchTest
  static final ArchRule production_code_must_not_depend_on_retired_sea_namespace =
      noClasses().should().dependOnClassesThat().resideInAnyPackage("org.zalava.sea..");

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..",
    "org.jobrunr..",
    "io.micrometer..",
    "gg.jte..",
    "org.telegram..",
    "tools.jackson.."
  };

  private static final String[] ADAPTER_PACKAGES = {
    "..adapter..",
    "..infrastructure..",
    "org.zalava.identity..api..",
    "org.zalava.assistant..api..",
    "org.zalava.web..api..",
    "org.zalava.modules..api..",
    "org.zalava.knowledge..api..",
    "org.zalava.tasks..api..",
    "..ui..",
    "..ws.."
  };

  @ArchTest
  static final ArchRule migrated_use_cases_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage(
              "org.zalava.identity.accounts.application..",
              "org.zalava.knowledge.application..",
              "org.zalava.modules.development.application..",
              "org.zalava.capabilities.operation.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule runtime_and_agent_collaboration_must_use_ports =
      noClasses()
          .that()
          .haveFullyQualifiedName("org.zalava.modules.runtime.ManagedZalavaRuntime")
          .or()
          .haveFullyQualifiedName("org.zalava.assistant.agent.AgentRequestTools")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..")
          .orShould()
          .dependOnClassesThat()
          .haveSimpleName("FileSystemModuleConfigurationStore");

  @ArchTest
  static final ArchRule domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domain_must_not_depend_on_adapters =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER_PACKAGES)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_must_not_depend_on_delivery_or_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER_PACKAGES)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_must_not_depend_on_framework_side_effects =
      noClasses()
          .that()
          .resideInAnyPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.web..", "org.jobrunr..", "io.micrometer..", "java.nio.file..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("..application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule task_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.tasks.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule task_domain_must_not_depend_on_task_application_or_adapters =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.tasks.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.zalava.tasks.application..", "org.zalava.tasks.adapter..");

  @ArchTest
  static final ArchRule task_application_must_not_depend_on_task_adapters_or_jobrunr =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.tasks.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.zalava.tasks.adapter..", "org.jobrunr..");

  @ArchTest
  static final ArchRule
      configuration_application_must_not_depend_on_configuration_adapters_or_frameworks =
          noClasses()
              .that()
              .resideInAnyPackage("org.zalava.platform.configuration.application..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "org.zalava.platform.configuration.adapter..",
                  "org.springframework..",
                  "java.nio.file..");

  @ArchTest
  static final ArchRule configuration_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.platform.configuration.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule memory_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.knowledge.memory.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule memory_application_must_not_depend_on_memory_adapters_or_filesystem =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.knowledge.memory.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.knowledge.memory.adapter..", "java.nio.file..", "org.springframework..");

  @ArchTest
  static final ArchRule conversation_domain_and_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage(
              "org.zalava.assistant.conversation.domain..",
              "org.zalava.assistant.conversation.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule catalog_application_must_not_depend_on_catalog_adapters_or_transport =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.catalog.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.modules.catalog.adapter..", "java.net.http..", "java.nio.file..");

  @ArchTest
  static final ArchRule catalog_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.catalog.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule runtime_application_must_not_depend_on_runtime_adapters_or_filesystem =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.runtime.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.modules.runtime.adapter..", "java.net..", "java.nio.file..");

  @ArchTest
  static final ArchRule runtime_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.runtime.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule runtime_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.runtime.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule web_extension_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.web.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule web_extension_application_must_not_depend_on_adapters_or_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.web.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.modules.web.adapter..", "org.springframework..", "java.nio.file..");

  @ArchTest
  static final ArchRule web_extension_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.modules.web.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule
      local_control_application_must_not_depend_on_local_control_adapters_or_frameworks =
          noClasses()
              .that()
              .resideInAnyPackage("org.zalava.web.control.application..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "org.zalava.web.control.adapter..", "org.springframework..", "java.nio.file..");

  @ArchTest
  static final ArchRule local_control_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.web.control.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule approval_storage_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.capabilities.approval.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule agent_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.agent.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule agent_application_must_not_depend_on_agent_adapters_or_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.agent.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.assistant.agent.adapter..", "org.springframework..", "java.nio.file..");

  @ArchTest
  static final ArchRule agent_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.agent.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule chat_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.chat.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule chat_application_must_not_depend_on_chat_adapters_or_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.chat.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.assistant.chat.adapter..", "org.springframework..", "java.nio.file..");

  @ArchTest
  static final ArchRule chat_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.chat.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule channel_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.channels.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule channel_application_must_not_depend_on_channel_adapters_or_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.channels.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.assistant.channels.adapter..",
              "org.springframework..",
              "org.telegram..",
              "java.nio.file..");

  @ArchTest
  static final ArchRule channel_ports_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.channels.application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule model_provider_domain_must_be_framework_independent =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.models.configuration.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES);

  @ArchTest
  static final ArchRule model_provider_application_must_not_depend_on_adapters_or_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("org.zalava.assistant.models.configuration.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.zalava.assistant.models.configuration.adapter.in..",
              "org.zalava.assistant.models.configuration.adapter.out..",
              "org.springframework..",
              "java.nio.file..");

  @ArchTest
  static final ArchRule inbound_adapters_must_not_depend_on_outbound_ports =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..adapter.in..",
              "org.zalava.identity..api..",
              "org.zalava.assistant..api..",
              "org.zalava.web..api..",
              "org.zalava.modules..api..",
              "org.zalava.knowledge..api..",
              "org.zalava.tasks..api..",
              "..ui..",
              "..ws..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application.port.out..")
          .allowEmptyShould(true);

  @Test
  void productionCodeDoesNotUseRetiredOperationsPackage() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("org.zalava");

    assertThat(classes.stream().map(javaClass -> javaClass.getPackageName()))
        .noneMatch(
            packageName ->
                packageName.equals("org.zalava.operations")
                    || packageName.startsWith("org.zalava.operations."));
  }

  @Test
  void domainRuleDetectsFrameworkCoupling() {
    var classes = new ClassFileImporter().importClasses(SpringCoupledDomainFixture.class);

    assertThat(domain_must_be_framework_independent.evaluate(classes).hasViolation()).isTrue();
  }

  @Test
  void domainRuleDetectsAdapterCoupling() {
    var classes = new ClassFileImporter().importClasses(AdapterCoupledDomainFixture.class);

    assertThat(domain_must_not_depend_on_adapters.evaluate(classes).hasViolation()).isTrue();
  }

  @Test
  void applicationRuleDetectsFileSystemCoupling() {
    var classes = new ClassFileImporter().importClasses(FileSystemCoupledApplicationFixture.class);

    assertThat(
            application_must_not_depend_on_framework_side_effects.evaluate(classes).hasViolation())
        .isTrue();
  }

  @Test
  void migratedApplicationRuleDetectsFrameworkCoupling() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                org.zalava.knowledge.application.fixture.SpringTransactionCoupledFixture.class,
                org.zalava.modules.development.application.fixture.JacksonCoupledFixture.class);
    assertThat(
            migrated_use_cases_must_be_framework_independent
                .evaluate(classes)
                .getFailureReport()
                .getDetails())
        .anyMatch(detail -> detail.contains("Transactional"))
        .anyMatch(detail -> detail.contains("ObjectMapper"));
  }
}
