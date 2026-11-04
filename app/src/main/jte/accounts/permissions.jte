@import java.util.List
@import java.util.Map
@import org.zalava.identity.accounts.application.UnifiedPermissions.Person
@import org.zalava.identity.accounts.application.UnifiedPermissions.ToolRow
@import org.zalava.identity.accounts.application.UnifiedPermissions.PageRow
@import org.zalava.identity.accounts.application.UnifiedPermissions.ModuleGroup
@import org.zalava.web.ui.PermissionFields
@import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice
@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@param Map<String, String> submitted
@param List<Person> people
@param List<ToolRow> toolRows
@param List<PageRow> appRows
@param List<ModuleGroup> groups
@param String subject
@param boolean personSelected
@param boolean memberSubject
@param boolean accountEnabled
@param String message = null
@param String error = null
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Permissions · Zalava</title><link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css"><link rel="stylesheet" href="/zalava-ui.css"><script src="/zalava-ui.js" defer></script></head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container settings-layout">@template.ui.settingsSections(current = "account-permissions")<div class="settings-content accounts-layout">
<h1 class="title is-3">Permissions</h1><p class="mb-3">Choose a role or person to manage their app access, tool access and Chat confirmations together.</p><p class="mb-4">Tool access decides whether a tool can be used. Confirmation decides whether an allowed Chat action asks first. App access includes the actions and data on that page.</p>
@if(message != null)<p class="notification is-success is-light" role="status">${message}</p>@endif
@if(error != null)<p class="notification is-danger is-light" role="alert">${error}</p>@endif
<form method="get" action="/zalava/accounts/permissions" class="mb-5"><label for="permission-subject" class="label">Manage permissions for</label><div class="select"><select id="permission-subject" name="subject"><optgroup label="Role defaults"><option value="member" selected="${subject.equals("member")}">All members</option><option value="administrator" selected="${subject.equals("administrator")}">All administrators</option></optgroup><optgroup label="People">@for(Person p : people)<option value="${p.id()}" selected="${subject.equals(p.id())}">${p.name()} · ${p.role()}${p.enabled() ? "" : " · Disabled"}</option>@endfor</optgroup></select></div> <button class="button is-link is-light" type="submit">Show permissions</button></form>
@if(!accountEnabled)<p class="notification is-warning is-light">This account is disabled and cannot use these permissions.</p>@endif
<div class="field" data-permission-filter-container hidden><label class="label" for="permission-filter">Find an app or tool</label><div class="control"><input class="input" id="permission-filter" type="search" data-permission-filter placeholder="Search by name or description" autocomplete="off"></div><p class="help" data-permission-filter-status role="status" aria-live="polite"></p></div>
<form method="post" action="/zalava/accounts/permissions/save" class="permissions-matrix">
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="subject" value="${subject}">
@if(groups.isEmpty())<p class="notification is-light">No apps or tools are available. Start a module to manage its permissions.</p>@endif
@for(ModuleGroup group : groups)
<section class="permission-module" data-permission-module><h2 class="title is-4">${group.name()}</h2><p class="is-size-7 has-text-grey mb-2">Module ID: ${group.id()}</p>
<div class="permission-column-headings" aria-hidden="true"><span>App or tool</span><span>Access</span><span>Chat confirmation</span></div>
@for(PageRow row : group.apps())
<div class="permission-matrix-row" data-permission-app="${row.page().moduleId()}:${row.page().pageId()}"><div><strong>${row.page().title()}</strong><p class="is-size-7">App · ${row.page().description()}</p><p class="is-size-7 has-text-grey">Page ID: ${row.page().pageId()}</p></div><div>
@if(!memberSubject)<span>${!accountEnabled ? "Not allowed · Account disabled" : row.page().routes().isEmpty() ? "Not allowed · App unavailable" : "Allowed · Administrator access"}</span>@else
<label class="label is-size-7" for="${PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId())}">App access</label><input type="hidden" name="before.${PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId())}" value="${row.choice()}"><div class="select is-fullwidth"><select id="${PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId())}" name="${PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId())}">@if(personSelected)<option value="INHERIT" selected="${PermissionFields.selected(submitted, PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId()), row.choice().name(), "INHERIT")}">Inherit role default</option>@endif<option value="ALLOW" selected="${PermissionFields.selected(submitted, PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId()), row.choice().name(), "ALLOW")}" disabled="${row.page().routes().isEmpty()}">Allow</option><option value="DENY" selected="${PermissionFields.selected(submitted, PermissionFields.field("app", row.page().moduleId(), "page", row.page().pageId()), row.choice().name(), "DENY")}">Deny</option></select></div><p class="help">Currently: ${row.allowed() ? "Allowed" : "Not allowed"} · ${row.source()}</p>@endif
</div><div class="is-size-7 has-text-grey">Not applicable</div></div>
@endfor
@for(ToolRow row : group.tools())
<div class="permission-matrix-row" data-permission-tool="${row.key().providerId()}:${row.key().toolName()}"><div><strong>${row.name()}</strong><p class="is-size-7">Tool · ${row.description()}</p><p class="is-size-7 has-text-grey">${row.providerName()} · Tool ID: ${row.key().toolName()}</p>@if(!row.available())<span class="tag is-warning is-light">Provider unavailable</span>@endif</div><div>
@if(row.hostAccessNote() != null)<span class="is-size-7">${row.hostAccessNote()}</span>@elseif(!memberSubject)<span class="is-size-7">${!accountEnabled ? "Not allowed · Account disabled" : row.available() ? "Allowed · Administrator" : "Not allowed · Provider unavailable"}</span>@else
<label class="label is-size-7" for="${PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName())}">Tool access</label><input type="hidden" name="before.${PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName())}" value="${row.accessChoice()}"><div class="select is-fullwidth"><select id="${PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName())}" name="${PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName())}">@if(personSelected)<option value="INHERIT" selected="${PermissionFields.selected(submitted, PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.accessChoice().name(), "INHERIT")}">Inherit role default</option>@endif<option value="ALLOW" selected="${PermissionFields.selected(submitted, PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.accessChoice().name(), "ALLOW")}" disabled="${!row.eligible()}">Allow</option><option value="DENY" selected="${PermissionFields.selected(submitted, PermissionFields.field("access", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.accessChoice().name(), "DENY")}">Deny</option></select></div><p class="help">Currently: ${row.accessEffective()} · ${row.accessSource()}</p>@endif
</div><div>
<label class="label is-size-7" for="${PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName())}">Chat confirmation</label><input type="hidden" name="before.${PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName())}" value="${row.confirmationChoice()}"><div class="select is-fullwidth"><select id="${PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName())}" name="${PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName())}"><option value="INHERIT" selected="${PermissionFields.selected(submitted, PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.confirmationChoice().name(), "INHERIT")}">${personSelected ? "Inherit role default" : (Boolean.FALSE.equals(row.sideEffecting()) || row.hostAccessNote() != null) ? "Default: no confirmation" : "Existing policy"}</option><option value="REQUIRE" selected="${PermissionFields.selected(submitted, PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.confirmationChoice().name(), "REQUIRE")}">Require confirmation</option><option value="AUTO" selected="${PermissionFields.selected(submitted, PermissionFields.field("confirmation", row.key().moduleId(), row.key().providerId(), row.key().toolName()), row.confirmationChoice().name(), "AUTO")}" disabled="${!row.available()}">Run without confirmation</option></select></div><p class="help">Currently: ${row.confirmationEffective() == Choice.REQUIRE ? "Require confirmation" : row.confirmationEffective() == Choice.AUTO ? "Run without confirmation" : (Boolean.FALSE.equals(row.sideEffecting()) || row.hostAccessNote() != null) ? "Run without confirmation by default" : "Existing approval policy"} · ${row.confirmationSource()}</p>
</div></div>
@endfor
</section>
@endfor
@if(!groups.isEmpty())<div class="permission-save"><button class="button is-link" type="submit">Save permissions</button> <span data-permission-save-status role="status" aria-live="polite"></span></div>@endif
</form>
</div></div></main></body></html>
