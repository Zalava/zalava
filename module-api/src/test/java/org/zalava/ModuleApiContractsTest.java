package org.zalava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.tasks.RecurringTaskSummary;
import org.zalava.tasks.TaskReference;
import org.zalava.tasks.TaskServiceResult;
import org.zalava.web.SeaWebRequest;
import org.zalava.web.SeaWebResponse;
import tools.jackson.databind.ObjectMapper;

class ModuleApiContractsTest {

  @Test
  void scopesDeclaredTypedServicesAndEnforcesTheirVersionAndType() {
    SeaServiceContract<String> contract = new SeaServiceContract<>("tasks", "v1", String.class);
    SeaServiceRequirement compatible =
        new SeaServiceRequirement("tasks", "v1", RequirementMode.REQUIRED);
    SeaServiceFactoryContext context =
        new SeaServiceFactoryContext(
            "module", Map.of(contract, "service"), Map.of("tasks", compatible), Map.of(), null);

    assertThat(context.moduleId()).isEqualTo("module");
    assertThat(context.configuration()).isEmpty();
    assertThat(context.secrets().resolve("missing")).isEmpty();
    assertThat(context.service(contract)).contains("service");
    assertThat(
            new SeaServiceFactoryContext("module", Map.of(), Map.of("tasks", compatible))
                .service(contract))
        .isEmpty();
    assertThat(
            new SeaServiceFactoryContext(
                    "module",
                    Map.of(contract, "service"),
                    Map.of(
                        "tasks", new SeaServiceRequirement("tasks", "*", RequirementMode.OPTIONAL)))
                .service(contract))
        .contains("service");

    assertThatIllegalStateException()
        .isThrownBy(
            () -> new SeaServiceFactoryContext("module", Map.of(), Map.of()).service(contract))
        .withMessageContaining("did not declare service tasks");
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new SeaServiceFactoryContext(
                        "module",
                        Map.of(),
                        Map.of(
                            "tasks",
                            new SeaServiceRequirement("tasks", "v2", RequirementMode.REQUIRED)))
                    .service(contract))
        .withMessageContaining("did not declare compatible service tasks");
    @SuppressWarnings({"rawtypes", "unchecked"})
    SeaServiceContract rawContract = contract;
    assertThatThrownBy(
            () ->
                new SeaServiceFactoryContext(
                        "module", Map.of(rawContract, 42), Map.of("tasks", compatible))
                    .service(contract))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void validatesServiceDeclarationsAndDefensivelyCopiesContextInputs() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceContract<>(" ", "v1", String.class));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceContract<>("tasks", " ", String.class));
    assertThatNullPointerException()
        .isThrownBy(() -> new SeaServiceContract<>("tasks", "v1", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceDescriptor("", "module", "v1"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceDescriptor("tasks", "", "v1"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceDescriptor("tasks", "module", ""));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceRequirement("", "v1", RequirementMode.REQUIRED));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceRequirement("tasks", "", RequirementMode.REQUIRED));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceRequirement("tasks", "v1", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SeaServiceFactoryContext(" ", Map.of(), Map.of()));

    Map<String, Object> configuration = new HashMap<>(Map.of("enabled", true));
    ProviderFactoryContext context = new ProviderFactoryContext(configuration, null, null, null);
    configuration.clear();
    assertThat(context.configuration()).containsEntry("enabled", true).isUnmodifiable();
    assertThat(context.secrets().resolve("anything")).isEmpty();
    assertThat(context.moduleSecrets()).isEmpty();
    assertThat(context.moduleServices()).isEmpty();
    assertThat(ProviderFactoryContext.empty().configuration()).isEmpty();
    assertThatIllegalArgumentException().isThrownBy(() -> context.forFactory(null, "factory"));
    assertThatIllegalArgumentException().isThrownBy(() -> context.forFactory("module", " "));
    assertThatIllegalStateException()
        .isThrownBy(() -> context.service(new SeaServiceContract<>("tasks", "v1", String.class)));

    SeaServiceContract<String> typedContract =
        new SeaServiceContract<>("typed-tasks", "v1", String.class);
    ProviderFactoryContext typedContext =
        new ProviderFactoryContext(
                Map.of(
                    "modules", Map.of("module", Map.of("factories", Map.of("factory", Map.of())))),
                FactorySecretAccess.none())
            .withTypedServices(
                Map.of(
                    "module",
                    new SeaServiceFactoryContext(
                        "module",
                        Map.of(typedContract, "typed-service"),
                        Map.of(
                            "typed-tasks",
                            new SeaServiceRequirement(
                                "typed-tasks", "v1", RequirementMode.REQUIRED)))));
    assertThat(typedContext.forFactory("module", "factory").service(typedContract))
        .contains("typed-service");
  }

  @Test
  void scopesFactoryConfigurationAndSecretsWithoutLeakingOtherModules() {
    char[] secret = "secret".toCharArray();
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of("module", Map.of("factories", Map.of("factory", Map.of("enabled", true))))),
            ignored -> java.util.Optional.of(secret),
            Map.of("module", ignored -> java.util.Optional.of("module-secret".toCharArray())),
            Map.of());

    ProviderFactoryContext factory = context.forFactory("module", "factory");
    assertThat(factory.configuration()).containsEntry("enabled", true);
    assertThat(factory.secrets().resolve("key").orElseThrow())
        .containsExactly("module-secret".toCharArray());
    assertThat(context.forFactory("module", "missing").configuration()).isEmpty();
    assertThat(context.forFactory("missing", "factory").configuration()).isEmpty();
    assertThat(
            new ProviderFactoryContext(Map.of("modules", "not-a-map"))
                .forFactory("module", "factory")
                .configuration())
        .isEmpty();
  }

  @Test
  void keepsConfigurationAndToolSchemasImmutableAtEveryNestedLevel() {
    Map<String, Object> nested = new HashMap<>(Map.of("type", "string"));
    List<Object> values = new ArrayList<>(List.of(nested));
    Map<String, Object> schema =
        new HashMap<>(Map.of("properties", Map.of("name", nested), "enum", values));

    SeaToolDescriptor descriptor =
        new SeaToolDescriptor("tool", "description", false, null, schema);
    nested.put("changed", true);
    values.clear();
    assertThat(descriptor.policyTags()).isEmpty();
    assertThat(descriptor.inputSchema()).containsKey("properties").isUnmodifiable();
    @SuppressWarnings("unchecked")
    Map<String, Object> copiedProperties =
        (Map<String, Object>) descriptor.inputSchema().get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> copiedName = (Map<String, Object>) copiedProperties.get("name");
    assertThat(copiedName).containsExactly(Map.entry("type", "string")).isUnmodifiable();
    assertThat((List<?>) descriptor.inputSchema().get("enum")).hasSize(1).isUnmodifiable();
    assertThatThrownBy(() -> new SeaToolDescriptor("tool", "description", false, List.of(), null))
        .isInstanceOf(NullPointerException.class);
    Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("value", null);
    assertThatNullPointerException().isThrownBy(() -> SeaToolInputSchemas.immutable(nullValue));

    assertThat(SeaToolInputSchemas.object(Map.of("name", SeaToolInputSchemas.string()), "name"))
        .containsEntry("type", "object")
        .containsEntry("additionalProperties", false);
    assertThat(SeaToolInputSchemas.integer()).containsEntry("type", "integer");
    assertThat(SeaToolInputSchemas.bool()).containsEntry("type", "boolean");
    assertThat(SeaToolInputSchemas.stringArray())
        .containsEntry("type", "array")
        .containsEntry("items", SeaToolInputSchemas.string());

    Map<String, Object> moduleSchema = new HashMap<>(Map.of("type", "object"));
    ModuleConfigurationDescriptor configuration = new ModuleConfigurationDescriptor(moduleSchema);
    moduleSchema.clear();
    assertThat(configuration.jsonSchema()).containsEntry("type", "object").isUnmodifiable();
    assertThat(ModuleConfigurationDescriptor.none().jsonSchema()).containsEntry("type", "object");
    assertThatNullPointerException().isThrownBy(() -> new ModuleConfigurationDescriptor(null));
  }

  @Test
  void exposesProviderAndModuleDefaultsAsSafeNoOpContracts() throws Exception {
    SeaProvider provider =
        new SeaProvider() {
          @Override
          public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(
                "provider",
                "module",
                "type",
                "Provider",
                "",
                "v1",
                ProviderCapabilities.toolsOnly(),
                null,
                null);
          }

          @Override
          public ProviderCapabilities capabilities() {
            return ProviderCapabilities.toolsOnly();
          }

          @Override
          public List<SeaToolDescriptor> listTools() {
            return List.of();
          }
        };
    InvocationContext invocation = new InvocationContext("actor", true, null);
    assertThat(invocation.attributes()).isEmpty();
    assertThat(InvocationContext.system().actorId()).isEqualTo("system");
    assertThat(provider.listResources()).isEmpty();
    assertThat(provider.listPrompts()).isEmpty();
    assertThatThrownBy(
            () -> provider.callTool("tool", new ObjectMapper().createObjectNode(), invocation))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("provider");
    assertThatThrownBy(() -> provider.readResource("resource", invocation))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                provider.resolvePrompt("prompt", new ObjectMapper().createObjectNode(), invocation))
        .isInstanceOf(UnsupportedOperationException.class);
    provider.close();

    SeaModule module =
        new SeaModule() {
          @Override
          public ModuleDescriptor descriptor() {
            return new ModuleDescriptor("module", "v1", "Module", "");
          }

          @Override
          public List<ProviderFactory> providerFactories() {
            return List.of();
          }
        };
    assertThat(module.configuration()).isEqualTo(ModuleConfigurationDescriptor.none());
    assertThat(module.verificationContributors()).isEmpty();
    assertThat(module.serviceFactories()).isEmpty();
    assertThat(module.serviceRequirements()).isEmpty();
    assertThat(module.webExtensions()).isEmpty();
    assertThat(ProviderCapabilities.toolsOnly().supportsTools()).isTrue();
    assertThat(ProviderCapabilities.toolsOnly().supportsStreaming()).isFalse();
  }

  @Test
  void normalizesWebRequestsAndProvidesResponseAndTaskResultFactories() {
    Map<String, List<String>> query =
        new HashMap<>(Map.of("q", new ArrayList<>(List.of("first", "second"))));
    SeaWebRequest request = new SeaWebRequest("GET", "/path", query, null, null, null);
    query.get("q").clear();
    assertThat(request.queryParameters())
        .containsEntry("q", List.of("first", "second"))
        .isUnmodifiable();
    assertThat(request.firstQueryParameter("q")).contains("first");
    assertThat(request.firstQueryParameter("missing")).isEmpty();
    assertThat(request.firstFormParameter("missing")).isEmpty();
    assertThat(
            new SeaWebRequest(
                    "POST",
                    "/",
                    Map.of(),
                    Map.of("name", List.of("sea")),
                    Map.of("id", "1"),
                    Map.of("actor", "a"))
                .firstFormParameter("name"))
        .contains("sea");
    assertThat(new SeaWebRequest("GET", "/", null, null, null, null).queryParameters()).isEmpty();
    assertThatThrownBy(
            () -> new SeaWebRequest("GET", "/", Map.of("q", null), Map.of(), Map.of(), Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThat(SeaWebResponse.html("body")).isEqualTo(new SeaWebResponse(200, "text/html", "body"));
    assertThat(SeaWebResponse.html(201, "body"))
        .isEqualTo(new SeaWebResponse(201, "text/html", "body"));

    TaskReference reference = new TaskReference("task-1");
    RecurringTaskSummary recurring = new RecurringTaskSummary("recurring-1", "Name", "Description");
    assertThat(TaskServiceResult.created(reference))
        .isEqualTo(new TaskServiceResult("created", reference, null));
    assertThat(TaskServiceResult.scheduled(reference))
        .isEqualTo(new TaskServiceResult("scheduled", reference, null));
    assertThat(TaskServiceResult.recurringScheduled(recurring))
        .isEqualTo(new TaskServiceResult("recurring_scheduled", null, recurring));
    assertThat(TaskServiceResult.recurringDeleted(recurring))
        .isEqualTo(new TaskServiceResult("recurring_deleted", null, recurring));
    assertThatNullPointerException().isThrownBy(() -> new TaskReference(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new TaskReference(" "));
  }

  @Test
  void copiesResultAndDescriptorCollectionsAndBuildsVerificationInvocation() {
    Map<String, Object> metadata = new HashMap<>(Map.of("source", "module"));
    SeaOperationResult success = new SeaOperationResult(true, "content", metadata);
    metadata.clear();
    assertThat(success.metadata()).containsEntry("source", "module").isUnmodifiable();
    assertThat(SeaOperationResult.success("content").success()).isTrue();
    assertThat(SeaOperationResult.failure("content").success()).isFalse();

    List<String> tags = new ArrayList<>(List.of("safe"));
    Map<String, String> scope = new HashMap<>(Map.of("group", "home"));
    ProviderDescriptor provider =
        new ProviderDescriptor(
            "provider",
            "module",
            "type",
            "Provider",
            "",
            "v1",
            ProviderCapabilities.toolsOnly(),
            tags,
            scope);
    tags.clear();
    scope.clear();
    assertThat(provider.policyTags()).containsExactly("safe").isUnmodifiable();
    assertThat(provider.scope()).containsEntry("group", "home").isUnmodifiable();

    SeaVerificationStep step =
        SeaVerificationStep.toolInvocation(
            "Run", "provider", "tool", true, true, Map.of("input", "value"));
    assertThat(step.path()).isEqualTo("/api/sea/providers/provider/tools/tool/invoke");
    assertThat(step.method()).isEqualTo("POST");
    assertThat(
            new SeaVerificationDescriptor("tools", "provider", List.of("tool"), List.of(step))
                .requiredTools())
        .containsExactly("tool");
  }

  @Test
  void allowsServiceFactoriesWithoutRetainedResourcesToClose() throws Exception {
    SeaServiceFactory<String> factory =
        new SeaServiceFactory<>() {
          @Override
          public SeaServiceDescriptor descriptor() {
            return new SeaServiceDescriptor("service", "module", "v1");
          }

          @Override
          public SeaServiceContract<String> contract() {
            return new SeaServiceContract<>("service", "v1", String.class);
          }

          @Override
          public String create(SeaServiceFactoryContext context) {
            return context.moduleId();
          }
        };
    assertThat(factory.create(new SeaServiceFactoryContext("module", null, null)))
        .isEqualTo("module");
    factory.close();
  }
}
