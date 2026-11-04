@import java.util.List
@import org.zalava.web.ui.ModulePageAccessController.Person
@import org.zalava.web.ui.ModulePageAccessController.Row
@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@param List<Person> members
@param List<Row> pages
@param String accountId = null
@param String message = null
@param String error = null
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>App access</title><link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css"><link rel="stylesheet" href="/zalava-ui.css"><script src="/zalava-ui.js" defer></script></head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container settings-layout">@template.ui.settingsSections(current = "accounts")<div class="settings-content"><p><a href="/zalava/accounts">← Accounts</a></p><h1 class="title is-3">App access</h1><p class="mb-4">Choose who can open each app and use its page actions. Tool access and Chat confirmation are managed separately.</p>
@if(message != null)<p class="notification is-success is-light" role="status">${message}</p>@endif
@if(error != null)<p class="notification is-danger is-light" role="alert">${error}</p>@endif
<form method="get" action="/zalava/accounts/app-access" class="mb-5"><label class="label" for="member">Individual member</label><div class="select"><select id="member" name="accountId"><option value="">Choose a member</option>@for(Person member : members)<option value="${member.id()}" selected="${member.id().equals(accountId)}">${member.login()}</option>@endfor</select></div> <button class="button is-link is-light" type="submit">Show individual settings</button></form>
@if(pages.isEmpty())<p>No module apps are available.</p>@else
@for(Row row : pages)<article class="box" data-app-access="${row.page().moduleId()}:${row.page().pageId()}"><h2 class="title is-5">${row.page().title()}</h2><p class="mb-3">${row.page().moduleDisplayName()} · ${row.page().description()}</p>
<form method="post" action="/zalava/accounts/app-access/default" class="mb-3"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="moduleId" value="${row.page().moduleId()}"><input type="hidden" name="pageId" value="${row.page().pageId()}"><label class="label" for="default-${row.page().moduleId()}-${row.page().pageId()}">Member role</label><div class="select"><select id="default-${row.page().moduleId()}-${row.page().pageId()}" name="allowed"><option value="false" selected="${!row.defaultAllowed()}">Not allowed</option><option value="true" selected="${row.defaultAllowed()}">Allowed</option></select></div> <button class="button is-link" type="submit">Save member default</button></form>
@if(row.override() != null)<p class="mb-2">Effective: ${row.override().equals("ALLOW") || (row.override().equals("INHERIT") && row.defaultAllowed()) ? "Allowed" : "Not allowed"} · ${row.override().equals("INHERIT") ? "Role default" : "Individual override"}</p><form method="post" action="/zalava/accounts/app-access/user"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="accountId" value="${accountId}"><input type="hidden" name="moduleId" value="${row.page().moduleId()}"><input type="hidden" name="pageId" value="${row.page().pageId()}"><label class="label" for="user-${row.page().moduleId()}-${row.page().pageId()}">Individual override</label><div class="select"><select id="user-${row.page().moduleId()}-${row.page().pageId()}" name="decision"><option value="INHERIT" selected="${row.override().equals("INHERIT")}">Inherit role default</option><option value="ALLOW" selected="${row.override().equals("ALLOW")}">Allow</option><option value="DENY" selected="${row.override().equals("DENY")}">Deny</option></select></div> <button class="button is-link" type="submit">Save individual access</button></form>@endif</article>@endfor@endif
</div></div></main></body></html>
