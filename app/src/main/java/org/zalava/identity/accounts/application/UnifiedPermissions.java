package org.zalava.identity.accounts.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccessDecision;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;

/** Coordinates the independent permission policies for an administrator's selected subject. */
public class UnifiedPermissions {
  private final MemberToolAccess tools;
  private final ChatToolConfirmation confirmation;
  private final ModulePageAccess pages;
  private final AccountLifecycle accounts;

  public UnifiedPermissions(
      MemberToolAccess tools,
      ChatToolConfirmation confirmation,
      ModulePageAccess pages,
      AccountLifecycle accounts) {
    this.tools = tools;
    this.confirmation = confirmation;
    this.pages = pages;
    this.accounts = accounts;
  }

  public Snapshot snapshot(String admin, String subject) {
    Subject selected = subject(subject);
    var memberDefaults = tools.defaults(admin);
    var people =
        accounts.list().stream()
            .map(a -> new Person(a.id().toString(), a.loginName(), a.role(), a.enabled()))
            .toList();
    Map<ToolKey, MemberToolAccess.ToolChoice> choices = new LinkedHashMap<>();
    tools.availableTools(admin).forEach(t -> choices.put(t.key(), t));
    Map<ToolKey, ChatToolConfirmation.Tool> confirmationTools = new LinkedHashMap<>();
    confirmation.tools(admin).forEach(t -> confirmationTools.put(t.key(), t));
    memberDefaults.forEach(
        d ->
            choices.putIfAbsent(
                d.key(),
                new MemberToolAccess.ToolChoice(
                    d.key(),
                    d.key().providerId(),
                    "Provider unavailable; saved settings remain active when it starts.",
                    false)));
    if (selected.person() != null && selected.role() == AccountRole.MEMBER)
      tools
          .decisions(admin, selected.person().id().toString())
          .forEach(
              d ->
                  choices.putIfAbsent(
                      d.key(),
                      new MemberToolAccess.ToolChoice(
                          d.key(),
                          d.key().providerId(),
                          "Provider unavailable; saved settings remain active when it starts.",
                          false)));
    confirmationTools.forEach(
        (key, t) ->
            choices.putIfAbsent(
                key,
                new MemberToolAccess.ToolChoice(key, t.providerName(), t.description(), false)));
    List<ToolRow> toolRows = new ArrayList<>();
    for (var tool : choices.values()) {
      var key = tool.key();
      var ct = confirmationTools.get(key);
      boolean available = ct != null && ct.available();
      Boolean sideEffecting = ct == null ? null : ct.sideEffecting();
      AccessDecision accessChoice = null;
      String accessEffective = "Allowed · Administrator", accessSource = "";
      if (selected.role() == AccountRole.MEMBER) {
        if (selected.person() == null) {
          boolean allowed =
              memberDefaults.stream().anyMatch(d -> d.key().equals(key) && d.allowed());
          accessChoice = allowed ? AccessDecision.ALLOW : AccessDecision.DENY;
          accessEffective = allowed && tool.eligible() ? "Allowed" : "Not allowed";
          accessSource = tool.eligible() ? "Member role default" : "Unavailable or restricted";
        } else {
          var e = tools.effective(admin, selected.person().id().toString(), key);
          accessChoice = e.override();
          accessEffective =
              e.allowed() && selected.person().enabled() && tool.eligible()
                  ? "Allowed"
                  : "Not allowed";
          accessSource = tool.eligible() ? e.source() : "Unavailable or restricted";
        }
      }
      var personConfirmation =
          selected.person() == null
              ? null
              : confirmation.effective(admin, selected.person().id().toString(), key);
      Choice roleChoice =
          selected.person() == null ? confirmation.roleDefault(admin, selected.role(), key) : null;
      Choice c = personConfirmation == null ? roleChoice : personConfirmation.override();
      Choice effective = personConfirmation == null ? roleChoice : personConfirmation.effective();
      String source = personConfirmation == null ? "Role default" : personConfirmation.source();
      toolRows.add(
          new ToolRow(
              key,
              friendly(key.toolName()),
              tool.providerName(),
              tool.description(),
              tool.eligible(),
              available,
              sideEffecting,
              ct == null ? null : ct.hostAccessNote(),
              accessChoice,
              accessEffective,
              accessSource,
              c,
              effective,
              source));
    }
    toolRows.sort(
        Comparator.comparing((ToolRow r) -> r.key().moduleId())
            .thenComparing(r -> r.providerName())
            .thenComparing(ToolRow::name));
    List<PageRow> appRows =
        pages.pages(admin).stream()
            .map(
                p -> {
                  boolean defaultAllowed = pages.memberDefault(admin, p.moduleId(), p.pageId());
                  AccessDecision override =
                      selected.person() != null && selected.role() == AccountRole.MEMBER
                          ? pages.override(
                              admin, selected.person().id().toString(), p.moduleId(), p.pageId())
                          : AccessDecision.INHERIT;
                  boolean allowed =
                      selected.role() == AccountRole.ADMIN
                          || (override == AccessDecision.ALLOW
                              || (override == AccessDecision.INHERIT && defaultAllowed));
                  if (selected.person() != null) {
                    allowed =
                        pages.allowed(selected.person().id().toString(), p.moduleId(), p.pageId());
                  } else if (p.routes().isEmpty()) {
                    allowed = false;
                  }
                  AccessDecision choice =
                      selected.role() == AccountRole.ADMIN
                          ? null
                          : selected.person() == null
                              ? (defaultAllowed ? AccessDecision.ALLOW : AccessDecision.DENY)
                              : override;
                  return new PageRow(
                      p,
                      choice,
                      allowed,
                      p.routes().isEmpty()
                          ? "App unavailable"
                          : selected.person() == null
                              ? "Role default"
                              : override == AccessDecision.INHERIT
                                  ? "Role default"
                                  : "Individual override");
                })
            .toList();
    return new Snapshot(
        people,
        selected.person() != null,
        selected.role() == AccountRole.MEMBER,
        selected.person() == null || selected.person().enabled(),
        toolRows,
        appRows);
  }

  public void saveBatch(String admin, String subject, List<Change> changes) {
    var current = snapshot(admin, subject);
    for (var change : changes) {
      switch (change) {
        case ToolAccessChange access -> {
          if (access.before() == access.decision()) continue;
          var row =
              current.toolRows().stream()
                  .filter(r -> r.key().equals(access.key()))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("Tool is no longer available"));
          if (row.accessChoice() != access.before())
            throw new IllegalArgumentException("Permissions changed; reload and try again");
          setToolAccess(admin, subject, access.key(), access.decision());
        }
        case ConfirmationChange confirmationChange -> {
          if (confirmationChange.before() == confirmationChange.decision()) continue;
          var row =
              current.toolRows().stream()
                  .filter(r -> r.key().equals(confirmationChange.key()))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("Tool is no longer available"));
          if (row.confirmationChoice() != confirmationChange.before())
            throw new IllegalArgumentException("Permissions changed; reload and try again");
          setConfirmation(admin, subject, confirmationChange.key(), confirmationChange.decision());
        }
        case AppAccessChange app -> {
          if (app.before() == app.decision()) continue;
          var row =
              current.appRows().stream()
                  .filter(
                      r ->
                          r.page().moduleId().equals(app.moduleId())
                              && r.page().pageId().equals(app.pageId()))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("App is no longer available"));
          if (row.choice() != app.before())
            throw new IllegalArgumentException("Permissions changed; reload and try again");
          setAppAccess(admin, subject, app.moduleId(), app.pageId(), app.decision());
        }
      }
    }
  }

  public sealed interface Change permits ToolAccessChange, ConfirmationChange, AppAccessChange {}

  public record ToolAccessChange(ToolKey key, AccessDecision before, AccessDecision decision)
      implements Change {}

  public record ConfirmationChange(ToolKey key, Choice before, Choice decision) implements Change {}

  public record AppAccessChange(
      String moduleId, String pageId, AccessDecision before, AccessDecision decision)
      implements Change {}

  public void setToolAccess(String admin, String subject, ToolKey key, AccessDecision decision) {
    Subject selected = subject(subject);
    requireMember(selected);
    if (selected.person() == null) {
      requireAllowOrDeny(decision);
      tools.defaultAccess(admin, key, decision == AccessDecision.ALLOW);
    } else if (decision == AccessDecision.INHERIT) {
      tools.inherit(admin, selected.person().id().toString(), key);
    } else {
      requireAllowOrDeny(decision);
      tools.decide(admin, selected.person().id().toString(), key, decision == AccessDecision.ALLOW);
    }
  }

  public void setConfirmation(String admin, String subject, ToolKey key, Choice choice) {
    Subject selected = subject(subject);
    if (selected.person() == null) confirmation.setRole(admin, selected.role(), key, choice);
    else confirmation.setUser(admin, selected.person().id().toString(), key, choice);
  }

  public void setAppAccess(
      String admin, String subject, String module, String page, AccessDecision decision) {
    Subject selected = subject(subject);
    requireMember(selected);
    if (selected.person() == null) {
      requireAllowOrDeny(decision);
      pages.setDefault(admin, module, page, decision == AccessDecision.ALLOW);
    } else pages.setOverride(admin, selected.person().id().toString(), module, page, decision);
  }

  private static void requireMember(Subject selected) {
    if (selected.role() != AccountRole.MEMBER)
      throw new IllegalArgumentException("Administrator access is fixed");
  }

  private static void requireAllowOrDeny(AccessDecision decision) {
    if (decision != AccessDecision.ALLOW && decision != AccessDecision.DENY)
      throw new IllegalArgumentException("Invalid choice");
  }

  private Subject subject(String value) {
    if (value.equals("member")) return new Subject(AccountRole.MEMBER, null);
    if (value.equals("administrator")) return new Subject(AccountRole.ADMIN, null);
    return accounts.list().stream()
        .filter(a -> a.id().toString().equals(value))
        .findFirst()
        .map(a -> new Subject(a.role(), a))
        .orElseThrow(NoSuchElementException::new);
  }

  private static String friendly(String name) {
    String text =
        name.replaceAll("([a-z])([A-Z])", "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .strip();
    return text.isEmpty() ? name : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private record Subject(AccountRole role, Account person) {}

  public record Snapshot(
      List<Person> people,
      boolean personSelected,
      boolean memberSubject,
      boolean accountEnabled,
      List<ToolRow> toolRows,
      List<PageRow> appRows) {
    public List<ModuleGroup> groups() {
      Map<String, List<PageRow>> apps = new LinkedHashMap<>();
      Map<String, List<ToolRow>> tools = new LinkedHashMap<>();
      Map<String, String> names = new LinkedHashMap<>();
      for (var row : appRows) {
        var id = row.page().moduleId();
        apps.computeIfAbsent(id, unused -> new ArrayList<>()).add(row);
        names.put(id, row.page().moduleDisplayName());
      }
      for (var row : toolRows) {
        var id = row.key().moduleId();
        tools.computeIfAbsent(id, unused -> new ArrayList<>()).add(row);
        names.putIfAbsent(id, id.equals("zalava-host") ? "Built-in Zalava" : friendly(id));
      }
      return names.entrySet().stream()
          .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
          .map(
              e ->
                  new ModuleGroup(
                      e.getKey(),
                      e.getValue(),
                      apps.getOrDefault(e.getKey(), List.of()),
                      tools.getOrDefault(e.getKey(), List.of())))
          .toList();
    }
  }

  public record ModuleGroup(String id, String name, List<PageRow> apps, List<ToolRow> tools) {}

  public record Person(String id, String name, AccountRole role, boolean enabled) {}

  public record ToolRow(
      ToolKey key,
      String name,
      String providerName,
      String description,
      boolean eligible,
      boolean available,
      Boolean sideEffecting,
      String hostAccessNote,
      AccessDecision accessChoice,
      String accessEffective,
      String accessSource,
      Choice confirmationChoice,
      Choice confirmationEffective,
      String confirmationSource) {
    public String hostAccessSummary() {
      if (hostAccessNote == null) return "";
      int end = hostAccessNote.indexOf('.');
      return end < 0 ? hostAccessNote : hostAccessNote.substring(0, end + 1);
    }
  }

  public record PageRow(
      RegisteredWebPage page, AccessDecision choice, boolean allowed, String source) {}
}
