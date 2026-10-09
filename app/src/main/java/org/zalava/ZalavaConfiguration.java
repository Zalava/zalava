package org.zalava;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.assistant.agent.Agent;
import org.zalava.assistant.agent.AgentRequestTools;
import org.zalava.assistant.agent.DefaultAgent;
import org.zalava.assistant.agent.WorkspaceInstructions;
import org.zalava.assistant.agent.adapter.out.springai.WorkspaceAgentPrompt;
import org.zalava.assistant.agent.adapter.out.system.AgentEnvironment;
import org.zalava.assistant.agent.application.DefaultAgentContextAssembler;
import org.zalava.assistant.agent.application.DefaultAgentExecution;
import org.zalava.assistant.agent.application.DefaultAgentRunQueries;
import org.zalava.assistant.agent.application.KnowledgeContextEnrichment;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.SkillContextEnrichment;
import org.zalava.assistant.agent.application.port.in.AgentExecution;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.application.port.out.AgentClock;
import org.zalava.assistant.agent.application.port.out.AgentContextAssembler;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.assistant.agent.application.port.out.AgentRunIdGenerator;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.application.port.out.AgentToolSelector;
import org.zalava.assistant.chat.adapter.out.agent.TaskCapturingChatAgent;
import org.zalava.assistant.chat.adapter.out.channels.ChannelMessageEventAdapter;
import org.zalava.assistant.chat.adapter.out.conversation.ConversationChatStore;
import org.zalava.assistant.chat.adapter.out.system.UuidChatConversationIdGenerator;
import org.zalava.assistant.chat.application.ActorChatUseCases;
import org.zalava.assistant.chat.application.DefaultChatUseCases;
import org.zalava.assistant.chat.application.UiExecutionStateQueries;
import org.zalava.assistant.chat.application.port.in.ChatCommands;
import org.zalava.assistant.chat.application.port.out.ChatAgent;
import org.zalava.assistant.chat.application.port.out.ChatApprovalCommands;
import org.zalava.assistant.chat.application.port.out.ChatConversationIdGenerator;
import org.zalava.assistant.chat.application.port.out.ChatConversationStore;
import org.zalava.assistant.chat.application.port.out.ChatMessageEvents;
import org.zalava.assistant.conversation.adapter.out.filesystem.FileSystemConversationStore;
import org.zalava.assistant.conversation.adapter.out.springai.MessageChatMemoryAdvisor;
import org.zalava.assistant.conversation.adapter.out.springai.MessageWindowChatMemory;
import org.zalava.assistant.conversation.adapter.out.springai.SpringAiChatMemoryRepository;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationRepository;
import org.zalava.assistant.conversation.application.port.out.ConversationStore;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackCatalog;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolIndex;
import org.zalava.capabilities.discovery.application.DefaultInstalledToolDiscovery;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.capabilities.operation.adapter.in.agent.ZalavaProviderTool;
import org.zalava.capabilities.operation.adapter.in.agent.ZalavaProviderToolInvoker;
import org.zalava.capabilities.operation.adapter.out.approval.ZalavaToolApprovalAdapter;
import org.zalava.capabilities.operation.adapter.out.runtime.ZalavaRuntimeProviderCatalog;
import org.zalava.capabilities.operation.application.DefaultProviderToolOperations;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.knowledge.adapter.in.agent.KnowledgeAgentTools;
import org.zalava.knowledge.memory.adapter.in.agent.MemoryPromotionTools;
import org.zalava.knowledge.memory.adapter.out.filesystem.FileSystemMemoryProposalStore;
import org.zalava.knowledge.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.knowledge.memory.application.ActorBoundMemoryQueries;
import org.zalava.knowledge.memory.application.DeterministicMemorySelector;
import org.zalava.knowledge.memory.application.ZalavaMemoryPromotions;
import org.zalava.knowledge.memory.application.port.in.MemoryPromotions;
import org.zalava.knowledge.memory.application.port.in.MemoryQueries;
import org.zalava.knowledge.memory.application.port.out.ActorMemoryStore;
import org.zalava.knowledge.memory.application.port.out.MemoryProposalStore;
import org.zalava.knowledge.skills.adapter.out.filesystem.FileSystemSkillActivationStore;
import org.zalava.knowledge.skills.adapter.out.filesystem.FileSystemSkillCatalog;
import org.zalava.knowledge.skills.adapter.out.filesystem.FileSystemSkillContentSource;
import org.zalava.knowledge.skills.application.DefaultSkillDiscovery;
import org.zalava.knowledge.skills.application.ZalavaSkillActivations;
import org.zalava.knowledge.skills.application.port.in.SkillActivations;
import org.zalava.knowledge.skills.application.port.in.SkillQueries;
import org.zalava.knowledge.skills.application.port.out.SkillActivationStore;
import org.zalava.knowledge.skills.application.port.out.SkillContentSource;
import org.zalava.knowledge.skills.domain.SkillContentPolicy;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.install.application.DefaultModuleQueries;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.modules.development.adapter.in.agent.CapabilityGapTools;
import org.zalava.modules.development.adapter.in.agent.DevelopmentRequestTools;
import org.zalava.modules.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.modules.development.application.port.in.DevelopmentRequestManagement;
import org.zalava.modules.development.application.port.in.DevelopmentWorkspaceExport;
import org.zalava.modules.runtime.ExternalZalavaModuleLoader;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.StaticZalavaModuleRegistry;
import org.zalava.modules.runtime.ZalavaModuleProperties;
import org.zalava.modules.runtime.ZalavaModuleRegistry;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.modules.web.adapter.out.runtime.ZalavaRuntimeWebExtensionModuleCatalog;
import org.zalava.modules.web.application.DefaultWebExtensionRoutes;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;
import org.zalava.platform.configuration.adapter.out.filesystem.FileSystemConfigurationStore;
import org.zalava.platform.configuration.adapter.out.spring.SpringConfigurationChangePublisher;
import org.zalava.platform.configuration.application.DefaultConfigurationManagement;
import org.zalava.platform.configuration.application.port.in.ConfigurationManagement;
import org.zalava.platform.configuration.application.port.out.ConfigurationChangePublisher;
import org.zalava.platform.configuration.application.port.out.ConfigurationStore;
import org.zalava.tasks.adapter.out.agent.ActorAgentTaskAgent;
import org.zalava.tasks.adapter.out.approval.ZalavaActorTaskApprovalDecisions;
import org.zalava.tasks.adapter.out.clarification.ZalavaActorTaskClarifications;
import org.zalava.tasks.adapter.out.filesystem.ActorFileSystemTaskStore;
import org.zalava.tasks.application.ActorTaskExecution;
import org.zalava.tasks.application.ActorTaskUseCases;
import org.zalava.tasks.application.DefaultTaskExecution;
import org.zalava.tasks.application.DefaultTaskUseCases;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskExecution;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.application.port.out.ActorTaskClarifications;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.application.port.out.TaskApprovalDecisions;
import org.zalava.tasks.application.port.out.TaskNotifier;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.capture.ActorTaskCreationContext;
import org.zalava.tasks.capture.TaskCreationContext;
import org.zalava.tasks.clarification.ClarificationTools;
import org.zalava.tasks.clarification.ZalavaClarifications;
import org.zalava.tasks.clarification.application.DefaultClarificationResponses;
import org.zalava.tasks.clarification.application.port.in.ClarificationResponses;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.web.control.adapter.out.runtime.ZalavaRuntimeVerificationCatalog;
import org.zalava.web.control.application.DefaultBootstrapVerificationQueries;
import org.zalava.web.control.application.InvocationLog;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries;

@Configuration
@EnableConfigurationProperties(ZalavaModuleProperties.class)
public class ZalavaConfiguration {

  @Bean
  public ActorExecutionContext actorExecutionContext() {
    return new ActorExecutionContext();
  }

  @Bean
  public ConfigurationStore configurationStore(Environment environment) {
    return FileSystemConfigurationStore.fromLocation(
        environment.getProperty("spring.allConfig.location"));
  }

  @Bean
  public FileSystemModuleConfigurationStore moduleConfigurationStore(Environment environment) {
    String configuredRoot = environment.getProperty("zalava.module-configuration.root");
    if (configuredRoot != null && !configuredRoot.isBlank()) {
      return new FileSystemModuleConfigurationStore(Path.of(configuredRoot));
    }
    String workspace = environment.getProperty("agent.workspace");
    if (workspace != null && workspace.startsWith("file:")) {
      try {
        Path parent = Path.of(URI.create(workspace)).getParent();
        if (parent != null) {
          return new FileSystemModuleConfigurationStore(parent);
        }
      } catch (IllegalArgumentException ignored) {
        // Buildpack AOT training can supply a non-hierarchical file URI.
      }
    }
    String location = environment.getProperty("spring.allConfig.location");
    Path privateConfiguration =
        location == null || location.isBlank()
            ? FileSystemConfigurationStore.pathFromImport(
                environment.getProperty("spring.config.import"))
            : FileSystemConfigurationStore.pathFromLocation(location);
    Path parent = privateConfiguration.getParent();
    return new FileSystemModuleConfigurationStore(parent == null ? Path.of(".") : parent);
  }

  @Bean
  public ConfigurationChangePublisher configurationChangePublisher(
      ApplicationEventPublisher eventPublisher) {
    return new SpringConfigurationChangePublisher(eventPublisher);
  }

  @Bean
  public ConfigurationManagement configurationManagement(
      ConfigurationStore configurationStore,
      ConfigurationChangePublisher configurationChangePublisher) {
    return new DefaultConfigurationManagement(configurationStore, configurationChangePublisher);
  }

  @Bean
  public org.zalava.assistant.channels.application.port.in.TelegramConfiguration
      telegramConfiguration(
          org.zalava.platform.configuration.application.port.in.ConfigurationManagement
              configurationManagement) {
    return new org.zalava.assistant.channels.application.TelegramConfigurationService(
        configurationManagement, configurationManagement);
  }

  @Bean
  public ActorMemoryStore actorMemoryStore(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    return new FileSystemMemoryStore(workspace.getFilePath());
  }

  @Bean
  public DeterministicMemorySelector memorySelector(
      @Value("${agent.context.memory-selection.enabled:true}") boolean enabled) {
    return new DeterministicMemorySelector(enabled);
  }

  @Bean
  public MemoryQueries memoryQueries(
      ActorMemoryStore actorMemoryStore,
      ActorExecutionContext actorExecutionContext,
      DeterministicMemorySelector memorySelector,
      @Value(
              "${agent.context.memory-selection.candidate-window:"
                  + ActorBoundMemoryQueries.DEFAULT_CANDIDATE_WINDOW
                  + "}")
          int candidateWindow) {
    return new ActorBoundMemoryQueries(
        actorMemoryStore, actorExecutionContext, memorySelector, candidateWindow);
  }

  @Bean
  public MemoryProposalStore memoryProposalStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemMemoryProposalStore(workspace.getFilePath());
  }

  @Bean
  public MemoryPromotions memoryPromotions(
      MemoryProposalStore memoryProposalStore, ActorMemoryStore actorMemoryStore) {
    return new ZalavaMemoryPromotions(
        memoryProposalStore, actorMemoryStore, java.time.Instant::now);
  }

  @Bean
  public org.zalava.knowledge.memory.application.port.in.MemoryManagement memoryManagement(
      ActorMemoryStore actorMemoryStore) {
    return new org.zalava.knowledge.memory.application.DefaultMemoryManagement(actorMemoryStore);
  }

  @Bean
  public MemoryPromotionTools memoryPromotionTools(
      MemoryPromotions memoryPromotions, ActorExecutionContext actorExecutionContext) {
    return new MemoryPromotionTools(memoryPromotions, actorExecutionContext);
  }

  @Bean
  public SkillQueries skillQueries(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      @Value("${agent.skills.active:}") String activeSkills,
      @Value("${agent.skills.max-results:" + DefaultSkillDiscovery.DEFAULT_MAX_RESULTS + "}")
          int maxResults)
      throws IOException {
    Path skillsDirectory = workspace.getFilePath().resolve("skills");
    return new DefaultSkillDiscovery(
        new FileSystemSkillCatalog(skillsDirectory),
        () -> List.of(),
        parseActiveSkills(activeSkills),
        maxResults);
  }

  static Set<String> parseActiveSkills(String configured) {
    if (configured == null || configured.isBlank()) {
      return Set.of();
    }
    return java.util.Arrays.stream(configured.split(","))
        .map(String::strip)
        .filter(value -> !value.isEmpty())
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  @Bean
  public SkillContentSource skillContentSource(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemSkillContentSource(workspace.getFilePath().resolve("skills"));
  }

  @Bean
  public SkillActivationStore skillActivationStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemSkillActivationStore(workspace.getFilePath());
  }

  @Bean
  public SkillActivations skillActivations(
      SkillQueries skillQueries,
      SkillContentSource skillContentSource,
      SkillActivationStore skillActivationStore,
      @Value(
              "${agent.skills.activation.max-characters:"
                  + SkillContentPolicy.MAXIMUM_CONTENT_LENGTH
                  + "}")
          int maximumContentCharacters) {
    return new ZalavaSkillActivations(
        skillQueries,
        skillContentSource,
        skillActivationStore,
        Instant::now,
        maximumContentCharacters);
  }

  @Bean
  public ModelBoundary modelBoundary(
      @Value("${agent.model-boundary.maximum-characters:64000}") int maximumCharacters,
      @Value("${agent.model-boundary.secret-values:}") String secretValues,
      org.springframework.context.ApplicationEventPublisher events) {
    return new ModelBoundary(maximumCharacters, secretValues, events::publishEvent);
  }

  @Bean
  public AgentContextAssembler agentContextAssembler(
      @Value(
              "${agent.context.character-budget:"
                  + DefaultAgentContextAssembler.DEFAULT_CHARACTER_BUDGET
                  + "}")
          int characterBudget,
      @Value(
              "${agent.context.memory-limit:"
                  + DefaultAgentContextAssembler.DEFAULT_MEMORY_LIMIT
                  + "}")
          int memoryLimit,
      @Value("${agent.context.knowledge-enrichment.enabled:true}")
          boolean knowledgeEnrichmentEnabled,
      @Value("${agent.context.skill-enrichment.enabled:true}") boolean skillEnrichmentEnabled,
      @Value("${agent.context.tool-summary-budget:0}") int toolSummaryBudget,
      @Value("${agent.context.tool-definition-budget:0}") int toolDefinitionBudget,
      @Value("${agent.context.memory-budget:0}") int memoryBudget,
      @Value("${agent.context.knowledge-budget:0}") int knowledgeBudget,
      @Value("${agent.context.skill-budget:0}") int skillBudget,
      MemoryQueries memoryQueries,
      org.zalava.knowledge.application.KnowledgeEvidenceQueries knowledgeEvidenceQueries,
      SkillActivations skillActivations,
      ActorExecutionContext actorExecutionContext,
      ModelBoundary modelBoundary) {
    return new DefaultAgentContextAssembler(
        characterBudget,
        memoryLimit,
        memoryQueries,
        new KnowledgeContextEnrichment(
            knowledgeEvidenceQueries,
            actorExecutionContext,
            modelBoundary,
            knowledgeEnrichmentEnabled),
        new SkillContextEnrichment(
            skillActivations, actorExecutionContext, modelBoundary, skillEnrichmentEnabled),
        new org.zalava.assistant.agent.application.ContextSourceBudgets(
            toolSummaryBudget, toolDefinitionBudget, memoryBudget, knowledgeBudget, skillBudget));
  }

  @Bean
  public AgentExecution agentExecution(
      AgentModel model,
      AgentToolSelector toolSelector,
      AgentContextAssembler contextAssembler,
      AgentRunStore runStore,
      AgentClock clock,
      AgentRunIdGenerator idGenerator,
      ModelBoundary modelBoundary,
      org.zalava.platform.observability.application.port.out.OperationalMetrics metrics) {
    return new DefaultAgentExecution(
        model,
        toolSelector,
        contextAssembler,
        runStore,
        clock,
        idGenerator,
        modelBoundary,
        metrics);
  }

  @Bean
  public AgentRunQueries agentRunQueries(AgentRunStore runStore) {
    return new DefaultAgentRunQueries(runStore);
  }

  @Bean
  public Agent agent(AgentExecution agentExecution) {
    return new DefaultAgent(agentExecution);
  }

  @Bean
  public ConversationStore conversationStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    return new FileSystemConversationStore(workspace.getFilePath());
  }

  @Bean
  public ConversationRepository conversationRepository(ConversationStore conversationStore) {
    return conversationStore;
  }

  @Bean
  public ActorConversations actorConversations(ConversationStore conversationStore) {
    return (ActorConversations) conversationStore;
  }

  @Bean
  public org.zalava.assistant.conversation.application.port.in.ConversationContinuation
      conversationContinuation(
          @org.springframework.beans.factory.annotation.Qualifier("actorConversations")
              ActorConversations actorConversations,
          @org.springframework.beans.factory.annotation.Qualifier("conversationStore")
              ConversationStore store,
          org.zalava.identity.channels.application.port.in.ChannelIdentityLinks links,
          AccountStore accounts) {
    return new org.zalava.assistant.conversation.application.DefaultConversationContinuation(
        actorConversations,
        (org.zalava.assistant.conversation.application.port.out.ConversationOriginStore) store,
        links,
        accounts);
  }

  @Bean
  public ChatMemoryRepository chatMemoryRepository(
      ConversationRepository conversationRepository, ActorConversations actorConversations) {
    return new SpringAiChatMemoryRepository(conversationRepository, actorConversations);
  }

  @Bean
  public ChatCommands chatCommands(
      AgentExecution agentExecution,
      ConversationRepository conversationRepository,
      TaskCreationContext taskCreationContext,
      org.zalava.assistant.channels.application.port.in.ChannelApprovalCommands
          channelApprovalCommands,
      org.zalava.assistant.channels.ChannelRegistry channelRegistry) {
    ChatAgent agent = new TaskCapturingChatAgent(agentExecution, taskCreationContext);
    ChatConversationStore conversations = new ConversationChatStore(conversationRepository);
    ChatApprovalCommands approvals = channelApprovalCommands::handle;
    ChatMessageEvents events = new ChannelMessageEventAdapter(channelRegistry);
    ChatConversationIdGenerator ids = new UuidChatConversationIdGenerator();
    return new DefaultChatUseCases(agent, approvals, conversations, events, ids);
  }

  @Bean
  public ActorChatUseCases actorChatUseCases(
      AgentExecution agentExecution,
      ActorConversations actorConversations,
      ActorTaskCreationContext taskCreationContext,
      ActorExecutionContext actorExecutionContext,
      AccountStore accountStore) {
    return new ActorChatUseCases(
        agentExecution,
        actorConversations,
        taskCreationContext,
        actorExecutionContext,
        accountStore);
  }

  @Bean
  public org.zalava.assistant.channels.application.port.in.ChannelApprovalCommands
      channelApprovalCommandPort(
          ZalavaToolApprovalRequests approvals,
          ProviderToolOperations operations,
          TaskCommands taskCommands,
          TaskQueries taskQueries) {
    return new org.zalava.assistant.channels.application.DefaultChannelApprovalCommands(
        new org.zalava.assistant.channels.adapter.out.approval.ZalavaChannelApprovalStore(
            approvals),
        new org.zalava.assistant.channels.adapter.out.approval.ProviderOperationChannelAdapter(
            operations),
        new org.zalava.assistant.channels.adapter.out.tasks.TaskChannelAdapter(
            taskCommands, taskQueries));
  }

  @Bean
  public org.zalava.web.onboarding.application.OnboardingWorkflow onboardingWorkflow(
      List<org.zalava.web.onboarding.OnboardingProvider> steps,
      org.zalava.platform.configuration.application.port.in.ConfigurationCommands
          configurationCommands) {
    return new org.zalava.web.onboarding.application.OnboardingWorkflow(
        steps, configurationCommands);
  }

  public static final String AGENT_MD = "AGENT.private.md";
  static final String BUILD_TRAINING_AGENT_PROMPT =
      """
            # Zalava build-time training workspace

            This temporary workspace exists only while the application records its
            JVM AOT cache. Do not access user files, credentials, or external services.
            """;
  static final String BUILD_TRAINING_ENVIRONMENT_INFO =
      "Temporary build-time workspace without user-owned environment context.";
  static final String PROVIDER_TOOL_GROUNDING_PROMPT =
      """

            # Zalava provider tool grounding

            Installed Zalava provider tools are the source of truth for provider-managed
            state such as shopping lists, files, jobs, and module-owned data.
            For requests that read or change provider-managed state, use an
            applicable direct provider callback when it is available. Otherwise use
            searchZalavaProviderTools, loadZalavaProviderTool when schema details are
            needed, and invokeZalavaProviderTool.
            If the user asks in another language, translate the provider/tool
            search query to likely English metadata terms before searching.

            If request-scoped Zalava discovery has no matching provider tool, use
            recommendModuleDevelopment to make the missing capability explicit.
            The recommendation does not create a request. Ask the user whether
            they want to create a reviewable module-development request, and
            call createModuleDevelopmentRequest only after an explicit yes and
            a complete structured contract. Never infer confirmation or begin
            workspace, Codex, candidate, installation, or activation work.

            Do not answer provider-state questions from chat memory alone. Do not
            claim that a side-effecting provider action is complete unless the
            provider tool result confirms execution. If the result is pending
            approval, say that approval is pending and include the approval request
            ID when present. If no provider tool was invoked, say that you cannot
            confirm the provider state or action.
            """;

  @Bean
  @ConditionalOnProperty(
      name = SpringAIModelProperties.CHAT_MODEL,
      havingValue = "unknown",
      matchIfMissing = true)
  public ChatModel chatModel() {
    return prompt ->
        new ChatResponse(
            List.of(
                new Generation(
                    new AssistantMessage(
                        "No AI model has been configured. If you did configure a model recently, restart Zalava manually for the changes to take effect."))));
  }

  @Bean
  public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
    return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).build();
  }

  @Bean
  public DefaultTaskUseCases taskUseCases(TaskStore taskStore, TaskScheduler taskScheduler) {
    return new DefaultTaskUseCases(taskStore, taskScheduler);
  }

  @Bean
  public TaskExecution taskExecution(
      TaskStore taskStore,
      TaskAgent taskAgent,
      TaskApprovalDecisions approvalDecisions,
      TaskNotifier taskNotifier,
      org.zalava.platform.observability.application.port.out.OperationalMetrics metrics) {
    return new DefaultTaskExecution(taskStore, taskAgent, approvalDecisions, taskNotifier, metrics);
  }

  @Bean
  public TaskExecutionContext taskExecutionContext() {
    return new TaskExecutionContext();
  }

  @Bean
  public ActorTaskStore actorTaskStore(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    return new ActorFileSystemTaskStore(workspace.getFilePath());
  }

  @Bean
  public org.zalava.tasks.application.port.in.ActorJobEvidenceQueries actorJobEvidenceQueries(
      org.zalava.tasks.application.port.out.ActorTaskStore actorTaskStore,
      org.zalava.tasks.application.port.out.ActorTaskSchedules schedules) {
    return new org.zalava.tasks.application.DefaultActorJobEvidenceQueries(
        actorTaskStore, schedules);
  }

  @Bean
  public ActorTaskCommands actorTaskCommands(
      ActorTaskStore actorTaskStore, TaskScheduler taskScheduler) {
    return new ActorTaskUseCases(actorTaskStore, taskScheduler);
  }

  @Bean
  public ActorTaskApprovalDecisions actorTaskApprovalDecisions(
      ZalavaToolApprovalRequests approvals) {
    return new ZalavaActorTaskApprovalDecisions(approvals);
  }

  @Bean
  public ActorTaskClarifications actorTaskClarifications(ZalavaClarifications clarifications) {
    return new ZalavaActorTaskClarifications(clarifications);
  }

  @Bean
  public ClarificationResponses clarificationResponses(
      ZalavaClarifications clarifications, ActorTaskCommands actorTaskCommands) {
    return new DefaultClarificationResponses(clarifications, actorTaskCommands);
  }

  @Bean
  public ClarificationTools clarificationTools(
      ZalavaClarifications clarifications,
      ActorExecutionContext actorExecutionContext,
      TaskExecutionContext taskExecutionContext) {
    return new ClarificationTools(clarifications, actorExecutionContext, taskExecutionContext);
  }

  @Bean
  public UiExecutionStateQueries uiExecutionStateQueries(
      ActorTaskCommands tasks, ActorTaskApprovalDecisions approvals) {
    return new UiExecutionStateQueries(tasks, approvals);
  }

  @Bean
  public ActorTaskAgent actorTaskAgent(
      Agent agent,
      AccountStore accountStore,
      ActorExecutionContext actorExecutionContext,
      TaskExecutionContext taskExecutionContext) {
    return new ActorAgentTaskAgent(
        agent, accountStore, actorExecutionContext, taskExecutionContext);
  }

  @Bean
  public ActorTaskExecution actorTaskExecution(
      ActorTaskStore actorTaskStore,
      ActorTaskAgent actorTaskAgent,
      ActorTaskNotifier actorTaskNotifier,
      ActorTaskApprovalDecisions approvals,
      ActorTaskClarifications clarifications) {
    return new ActorTaskExecution(
        actorTaskStore, actorTaskAgent, actorTaskNotifier, approvals, clarifications);
  }

  @Bean
  public DevelopmentRequestTools developmentRequestTools(
      DevelopmentRequestManagement requests,
      DevelopmentWorkspaceExport workspaces,
      DevelopmentCandidateSubmission candidates) {
    return new DevelopmentRequestTools(requests, workspaces, candidates);
  }

  @Bean
  public CapabilityGapTools capabilityGapTools() {
    return new CapabilityGapTools();
  }

  @Bean
  public org.zalava.api.extensions.tasks.TaskService zalavaTaskService(
      TaskCommands taskCommands,
      TaskQueries taskQueries,
      ActorTaskCommands actorTaskCommands,
      ActorTaskCreationContext actorTaskCreationContext,
      ActorExecutionContext actorExecutionContext) {
    return new org.zalava.tasks.adapter.in.zalava.ZalavaTaskService(
        taskCommands,
        taskQueries,
        actorTaskCommands,
        actorTaskCreationContext,
        actorExecutionContext);
  }

  @Bean
  public ProviderFactoryContext zalavaProviderFactoryContext(
      ZalavaModuleProperties moduleProperties,
      FileSystemModuleConfigurationStore configurations,
      org.zalava.api.extensions.tasks.TaskService taskService) {
    java.util.Map<String, Object> modules = new java.util.HashMap<>(moduleProperties.modules());
    java.util.Map<String, org.zalava.api.FactorySecretAccess> secrets = new java.util.HashMap<>();
    configurations
        .activeConfigurations()
        .forEach(
            (moduleId, snapshot) -> {
              modules.put(moduleId, java.util.Map.of("factories", snapshot.factories()));
              secrets.put(moduleId, configurations.secrets(moduleId));
            });
    return new ProviderFactoryContext(
        java.util.Map.of("modules", java.util.Map.copyOf(modules)),
        org.zalava.api.FactorySecretAccess.none(),
        secrets,
        java.util.Map.of(
            "zalava-module-tasks",
            java.util.Map.of(org.zalava.api.extensions.tasks.TaskService.class, taskService)));
  }

  ProviderFactoryContext zalavaProviderFactoryContext(
      ZalavaModuleProperties moduleProperties, FileSystemModuleConfigurationStore configurations) {
    java.util.Map<String, Object> modules = new java.util.HashMap<>(moduleProperties.modules());
    java.util.Map<String, org.zalava.api.FactorySecretAccess> secrets = new java.util.HashMap<>();
    configurations
        .activeConfigurations()
        .forEach(
            (moduleId, snapshot) -> {
              modules.put(moduleId, java.util.Map.of("factories", snapshot.factories()));
              secrets.put(moduleId, configurations.secrets(moduleId));
            });
    return new ProviderFactoryContext(
        java.util.Map.of("modules", java.util.Map.copyOf(modules)),
        org.zalava.api.FactorySecretAccess.none(),
        secrets);
  }

  ProviderFactoryContext zalavaProviderFactoryContext() {
    return ProviderFactoryContext.empty();
  }

  @Bean
  public ExternalZalavaModuleLoader externalZalavaModuleLoader(
      EnabledModuleRegistry enabledModuleRegistry, ProviderFactoryContext providerFactoryContext) {
    return new ExternalZalavaModuleLoader(enabledModuleRegistry, providerFactoryContext);
  }

  @Bean
  public ModuleQueries moduleQueries(EnabledModuleRegistry enabledModuleRegistry) {
    return new DefaultModuleQueries(enabledModuleRegistry);
  }

  @Bean
  public ZalavaModuleRegistry zalavaModuleRegistry(
      Set<ZalavaModule> zalavaModules, ExternalZalavaModuleLoader externalZalavaModuleLoader) {
    return new StaticZalavaModuleRegistry(
        mergeZalavaModules(zalavaModules, externalZalavaModuleLoader.loadModules()));
  }

  static List<ZalavaModule> mergeZalavaModules(
      Set<ZalavaModule> builtIns, List<ZalavaModule> externalModules) {
    List<ZalavaModule> modules = new ArrayList<>(builtIns);
    modules.addAll(externalModules);
    return List.copyOf(modules);
  }

  @Bean
  public ZalavaRuntime zalavaRuntime(
      ZalavaModuleRegistry zalavaModuleRegistry,
      Set<ZalavaModule> zalavaModules,
      EnabledModuleRegistry enabledModuleRegistry,
      FileSystemModuleLifecycleStore lifecycle,
      FileSystemModuleConfigurationStore configurations,
      ProviderFactoryContext zalavaProviderFactoryContext) {
    return new ManagedZalavaRuntime(
        zalavaModuleRegistry,
        zalavaModules,
        enabledModuleRegistry,
        lifecycle,
        configurations,
        zalavaProviderFactoryContext);
  }

  @Bean
  public WebExtensionRoutes webExtensionRoutes(ZalavaRuntime zalavaRuntime) {
    return new DefaultWebExtensionRoutes(new ZalavaRuntimeWebExtensionModuleCatalog(zalavaRuntime));
  }

  @Bean
  public BootstrapVerificationQueries bootstrapVerificationQueries(ZalavaRuntime zalavaRuntime) {
    return new DefaultBootstrapVerificationQueries(
        new ZalavaRuntimeVerificationCatalog(zalavaRuntime));
  }

  @Bean
  public InvocationLog invocationLog() {
    return new InvocationLog();
  }

  @Bean
  public ProviderToolOperations providerToolOperations(
      ZalavaRuntime zalavaRuntime,
      ZalavaToolApprovalRequests approvalRequests,
      List<ToolInvocationObserver> observers) {
    return new DefaultProviderToolOperations(
        new ZalavaRuntimeProviderCatalog(zalavaRuntime),
        new ZalavaToolApprovalAdapter(approvalRequests),
        observers,
        new org.zalava.capabilities.operation.adapter.out.json.JacksonToolArgumentDecoder());
  }

  @Bean
  public ToolDiscovery toolDiscovery(ZalavaRuntime zalavaRuntime) {
    return new DefaultInstalledToolDiscovery(new ZalavaRuntimeProviderCatalog(zalavaRuntime));
  }

  @Bean
  public ToolIndex springAiToolIndex() {
    return new ZalavaToolIndex();
  }

  @Bean
  public ZalavaToolCallbackCatalog zalavaToolCallbackCatalog(
      ZalavaRuntime zalavaRuntime,
      ProviderToolOperations providerToolOperations,
      TaskExecutionContext taskExecutionContext,
      ActorExecutionContext actorExecutionContext) {
    return new ZalavaToolCallbackCatalog(
        new ZalavaRuntimeProviderCatalog(zalavaRuntime),
        new ZalavaProviderToolInvoker(
            providerToolOperations, taskExecutionContext, actorExecutionContext));
  }

  @Bean
  public AgentRequestTools agentRequestTools(
      @Value("${agent.workspace:Unknown}") Resource workspace,
      DevelopmentRequestTools developmentRequestTools,
      CapabilityGapTools capabilityGapTools,
      ClarificationTools clarificationTools,
      MemoryPromotionTools memoryPromotionTools,
      ProviderToolOperations providerToolOperations,
      ToolDiscovery toolDiscovery,
      ZalavaToolCallbackCatalog callbackCatalog,
      org.zalava.capabilities.discovery.application.port.in.RemoteCapabilityDiscovery
          remoteDiscovery,
      TaskExecutionContext taskExecutionContext,
      ActorExecutionContext actorExecutionContext,
      org.zalava.knowledge.application.KnowledgeEvidenceQueries knowledgeEvidence,
      ModelBoundary modelBoundary,
      ToolIndex springAiToolIndex,
      @Value("${agent.tool-selection.tool-search.enabled:false}") boolean toolSearchEnabled,
      List<ToolInvocationObserver> knowledgeObservers)
      throws IOException {
    Path skillsDirectory = skillsDir(workspace);
    List<Object> bootstrapTools =
        new ArrayList<>(
            List.of(
                developmentRequestTools,
                clarificationTools,
                memoryPromotionTools,
                new ZalavaProviderTool(
                    providerToolOperations,
                    toolDiscovery,
                    taskExecutionContext,
                    actorExecutionContext)));
    bootstrapTools.add(
        new KnowledgeAgentTools(
            knowledgeEvidence,
            actorExecutionContext,
            modelBoundary,
            new org.zalava.knowledge.application.KnowledgeToolObservation(knowledgeObservers)));
    if (hasConfiguredSkills(skillsDirectory)) {
      bootstrapTools.add(
          SkillsTool.builder().addSkillsDirectory(skillsDirectory.toString()).build());
    }
    return new AgentRequestTools(
        bootstrapTools,
        toolDiscovery,
        callbackCatalog,
        List.of(capabilityGapTools),
        remoteDiscovery,
        new org.zalava.assistant.agent.PolicyFilteredToolSearch(
            springAiToolIndex, toolSearchEnabled));
  }

  @Bean
  @DependsOn({"mcpHeaderCustomizer"})
  public ChatClient chatClient(
      ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, WorkspaceAgentPrompt prompt) {

    String agentPrompt = prompt.text();

    chatClientBuilder
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .defaultSystem(
            p ->
                p.text(agentPrompt)
                    .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info()))
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
    return chatClientBuilder.build();
  }

  @Bean
  public WorkspaceAgentPrompt agentSystemPrompt(
      WorkspaceInstructions instructions, @Value("${agent.workspace}") Resource workspace) {
    return new WorkspaceAgentPrompt(
        () -> {
          try {
            return instructions.current()
                + System.lineSeparator()
                + readWorkspaceFile(workspace, "INFO.md", BUILD_TRAINING_ENVIRONMENT_INFO)
                + System.lineSeparator()
                + PROVIDER_TOOL_GROUNDING_PROMPT;
          } catch (IOException exception) {
            throw new IllegalStateException("Unable to read agent environment", exception);
          }
        });
  }

  private static Path skillsDir(Resource workspace) throws IOException {
    Path skillsDir = workspace.getFilePath().resolve("skills");
    Files.createDirectories(skillsDir);
    return skillsDir;
  }

  private static String readWorkspaceFile(Resource workspace, String name, String fallback)
      throws IOException {
    Resource resource = workspace.createRelative(name);
    return resource.exists() ? resource.getContentAsString(StandardCharsets.UTF_8) : fallback;
  }

  static boolean hasConfiguredSkills(Path skillsDirectory) throws IOException {
    try (var paths = Files.walk(skillsDirectory)) {
      return paths.anyMatch(path -> path.getFileName().toString().equals("SKILL.md"));
    }
  }
}
