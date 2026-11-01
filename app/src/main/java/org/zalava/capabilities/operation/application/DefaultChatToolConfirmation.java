package org.zalava.capabilities.operation.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.out.ChatToolConfirmationStore;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

public final class DefaultChatToolConfirmation implements ChatToolConfirmation {
  private final AccountLifecycle accounts;
  private final ChatToolConfirmationStore store;
  private final ProviderCatalog catalog;
  private final Clock clock;

  public DefaultChatToolConfirmation(
      AccountLifecycle accounts,
      ChatToolConfirmationStore store,
      ProviderCatalog catalog,
      Clock clock) {
    this.accounts = accounts;
    this.store = store;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  public List<Tool> tools(String administratorId) {
    requireAdmin(administratorId);
    Map<ToolKey, Tool> tools = new LinkedHashMap<>();
    catalog
        .providers()
        .forEach(
            provider ->
                provider.listTools().stream()
                    .forEach(
                        tool -> {
                          ToolKey key =
                              new ToolKey(
                                  provider.descriptor().moduleId(),
                                  provider.descriptor().providerId(),
                                  tool.name());
                          tools.put(
                              key,
                              new Tool(
                                  key,
                                  provider.descriptor().displayName(),
                                  tool.description(),
                                  true,
                                  tool.sideEffecting()));
                        }));
    store
        .savedTools()
        .forEach(
            key ->
                tools.putIfAbsent(
                    key,
                    new Tool(
                        key,
                        key.providerId(),
                        "Provider unavailable. Saved settings remain active when it starts; you can require confirmation or restore the existing policy now.",
                        false,
                        null)));
    return List.copyOf(tools.values());
  }

  @Override
  public Effective effective(String administratorId, String accountId, ToolKey key) {
    requireAdmin(administratorId);
    Account account = account(accountId);
    Optional<Boolean> user = store.user(accountId, key);
    Optional<Boolean> role = store.role(account.role(), key);
    Optional<Boolean> selected = user.isPresent() ? user : role;
    return new Effective(
        choice(user),
        choice(role),
        choice(selected),
        user.isPresent()
            ? "User override"
            : role.isPresent() ? "Role default" : "Existing approval policy");
  }

  @Override
  public Choice roleDefault(String administratorId, AccountRole role, ToolKey key) {
    requireAdmin(administratorId);
    return choice(store.role(role, key));
  }

  @Override
  public void setRole(String administratorId, AccountRole role, ToolKey key, Choice choice) {
    requireAdmin(administratorId);
    validate(key, choice);
    store.setRole(administratorId, role, key, choice, clock.instant());
  }

  @Override
  public void setUser(String administratorId, String accountId, ToolKey key, Choice choice) {
    requireAdmin(administratorId);
    account(accountId);
    validate(key, choice);
    store.setUser(administratorId, accountId, key, choice, clock.instant());
  }

  @Override
  public Optional<Boolean> requireFor(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    if (!"web".equals(context.attributes().get("permissionPolicyChannel"))
        || context.attributes().containsKey("taskReference")
        || context.attributes().containsKey("actorTaskReference")
        || !List.of("spring-ai-callback", "zalava-provider-tool")
            .contains(context.attributes().getOrDefault("source", ""))) return Optional.empty();
    if (!tool.sideEffecting()
        && "agent".equals(context.actorId())
        && !context.attributes().containsKey("accountRole")) return Optional.empty();
    Account actor;
    try {
      actor = account(context.actorId());
    } catch (IllegalArgumentException missing) {
      return Optional.of(true);
    }
    if (!actor.enabled() || !actor.role().name().equals(context.attributes().get("accountRole")))
      return Optional.of(true);
    ToolKey key =
        new ToolKey(
            provider.descriptor().moduleId(), provider.descriptor().providerId(), tool.name());
    Optional<Boolean> user = store.user(context.actorId(), key);
    return user.isPresent() ? user : store.role(actor.role(), key);
  }

  @Override
  public boolean mayConsumeReadApproval(InvocationContext context) {
    try {
      Account actor = account(context.actorId());
      return actor.enabled() && actor.role().name().equals(context.attributes().get("accountRole"));
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private void validate(ToolKey key, Choice choice) {
    if (choice == null) throw new IllegalArgumentException("Choose a confirmation policy");
    if (choice == Choice.INHERIT || (choice == Choice.REQUIRE && store.savedTools().contains(key)))
      return;
    var provider =
        catalog
            .findProvider(key.providerId())
            .filter(p -> p.descriptor().moduleId().equals(key.moduleId()))
            .orElseThrow(() -> new IllegalArgumentException("Tool is unavailable"));
    if (provider.listTools().stream().noneMatch(tool -> tool.name().equals(key.toolName())))
      throw new IllegalArgumentException("Tool is unavailable");
  }

  private void requireAdmin(String id) {
    Account admin = account(id);
    if (!admin.enabled() || admin.role() != AccountRole.ADMIN)
      throw new SecurityException("Administrator access is required");
  }

  private Account account(String id) {
    try {
      return accounts
          .findById(new AccountId(UUID.fromString(id)))
          .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    } catch (NullPointerException invalid) {
      throw new IllegalArgumentException("Account not found", invalid);
    }
  }

  private static Choice choice(Optional<Boolean> value) {
    return value.map(required -> required ? Choice.REQUIRE : Choice.AUTO).orElse(Choice.INHERIT);
  }
}
