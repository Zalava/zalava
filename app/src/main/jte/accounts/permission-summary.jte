@import java.util.List
@import org.zalava.identity.accounts.application.UnifiedPermissions.Person
@import org.zalava.identity.accounts.application.UnifiedPermissions.ModuleGroup
@import org.zalava.identity.accounts.application.UnifiedPermissions.PageRow
@import org.zalava.identity.accounts.application.UnifiedPermissions.ToolRow
@import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice
@import org.zalava.web.ui.navigation.NavigationModel
@param String subject
@param Person person
@param List<ModuleGroup> groups
@param boolean accountEnabled
@param NavigationModel navigation
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Permissions for ${person.name()} · Zalava</title><link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css"><link rel="stylesheet" href="/zalava-ui.css"><script src="/zalava-ui.js" defer></script></head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container settings-layout">@template.ui.settingsSections(current = "accounts")<div class="settings-content accounts-layout">
<a href="/zalava/accounts">← Accounts</a><h1 class="title is-3 mt-3">Permissions for ${person.name()}</h1><p>Current access, including permissions inherited from the role.</p>
@if(!accountEnabled)<p class="notification is-warning is-light">This account is disabled and cannot use these permissions.</p>@endif
@for(ModuleGroup group : groups)<section class="permission-module"><h2 class="title is-4">${group.name()}</h2><p class="is-size-7 has-text-grey">Module ID: ${group.id()}</p>
@for(PageRow row : group.apps())<div class="permission-summary-row"><div><strong>${row.page().title()}</strong><p class="is-size-7 has-text-grey">App</p></div><div><span class="is-size-7 has-text-grey">Access</span><p>${row.allowed() ? "Allowed" : "Not allowed"} · ${row.source()}</p></div><div></div></div>@endfor
@for(ToolRow row : group.tools())<div class="permission-summary-row"><div><strong>${row.name()}</strong><p class="is-size-7 has-text-grey">Tool · ${row.key().toolName()}</p></div><div><span class="is-size-7 has-text-grey">Access</span><p>${row.hostAccessNote() != null ? row.hostAccessSummary() : row.accessEffective() + " · " + row.accessSource()}</p></div><div><span class="is-size-7 has-text-grey">Chat</span><p>${row.confirmationEffective() == Choice.REQUIRE ? "Confirmation required" : row.confirmationEffective() == Choice.AUTO ? "No confirmation" : (Boolean.FALSE.equals(row.sideEffecting()) || row.hostAccessNote() != null) ? "No confirmation by default" : "Existing approval policy"}</p></div></div>@endfor
</section>@endfor
<a class="button is-link" href="/zalava/accounts/permissions?subject=${subject}">Manage permissions</a>
</div></div></main></body></html>
